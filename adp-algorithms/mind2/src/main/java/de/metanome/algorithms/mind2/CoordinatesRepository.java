package de.metanome.algorithms.mind2;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.metanome.algorithm_integration.AlgorithmExecutionException;
import de.metanome.algorithm_integration.ColumnIdentifier;
import de.metanome.algorithm_integration.ColumnPermutation;
import de.metanome.algorithm_integration.algorithm_execution.FileCreationException;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.RelationalInput;
import de.metanome.algorithm_integration.input.TableInputGenerator;
import de.metanome.algorithm_integration.result_receiver.InclusionDependencyResultReceiver;
import de.metanome.algorithm_integration.results.InclusionDependency;
import de.metanome.algorithms.mind2.configuration.Mind2Configuration;
import de.metanome.algorithms.mind2.model.AttributeValuePosition;
import de.metanome.algorithms.mind2.model.UindCoordinates;
import de.metanome.algorithms.mind2.model.ValuePositions;
import de.metanome.algorithms.mind2.utils.AttributeIterator;
import de.metanome.algorithms.mind2.utils.UindCoordinatesReader;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static com.google.common.collect.Iterables.getOnlyElement;
import static de.metanome.util.Collectors.toImmutableList;
import static de.metanome.util.Collectors.toImmutableSet;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

public class CoordinatesRepository {

    private static final Logger log = Logger.getLogger(CoordinatesRepository.class.getName());

    private final Mind2Configuration config;
    private final ImmutableSet<InclusionDependency> uinds;
    private final Map<Integer, Path> uindToPath = new HashMap<>();
    private final Map<Integer, InclusionDependency> idToUind = new HashMap<>();

    @Inject
    public CoordinatesRepository(Mind2Configuration config, ImmutableSet<InclusionDependency> uinds) {
        this.config = config;
        this.uinds = uinds;
    }



    public UindCoordinatesReader getReader(Integer uindId) throws AlgorithmExecutionException {
        if (!uindToPath.containsKey(uindId)) {
            throw new AlgorithmExecutionException(format("No coordinates file found for UIND id %d", uindId));
        }
        try {
            return new UindCoordinatesReader(uindId, Files.newBufferedReader(uindToPath.get(uindId)));
        } catch (IOException e) {
            throw new AlgorithmExecutionException(format("Error reading coordinates file for uind %s", uindId), e);
        }
    }

    public IntSet storeUindCoordinates() throws AlgorithmExecutionException {
        ImmutableMap<ColumnIdentifier, TableInputGenerator> attributes =
                getRelationalInputMap(config.getInputGenerators());
        ImmutableMap<String, ColumnIdentifier> indexColumns = getIndexColumns(attributes);
        // TODO: Remove sorting. Only used to have consistent UIND IDs between runs.
        ImmutableList<InclusionDependency> sortedUinds = uinds.stream()
                .sorted(Comparator.comparing(InclusionDependency::toString))
                .collect(toImmutableList());
        int uindId = 0;
        for (InclusionDependency uind : sortedUinds) {
            List<ValuePositions> uindCoordinates = generateCoordinates(uind, attributes, indexColumns);
            Path path = getPath();
            try {
                writeToFile(uindCoordinates, path);
                uindToPath.put(uindId, path);
                idToUind.put(uindId, uind);
                uindId++;
            } catch (IOException e) {
                throw new AlgorithmExecutionException(
                        format("Error writing uind coordinates for uind %s to file %s", uind, path), e);
            }
        }
        return new IntOpenHashSet(idToUind.keySet());
    }

    public void collectInds(Set<IntSet> maxInds) throws AlgorithmExecutionException {
        InclusionDependencyResultReceiver resultReceiver = config.getResultReceiver();
        for (IntSet maxInd : maxInds) {
            ImmutableList<ColumnIdentifier> referencedIds = maxInd.stream()
                    .map(idToUind::get)
                    .map(InclusionDependency::getReferenced)
                    .map(ColumnPermutation::getColumnIdentifiers)
                    .flatMap(List::stream)
                    .collect(toImmutableList());
            ColumnPermutation referenced = new ColumnPermutation();
            referenced.setColumnIdentifiers(referencedIds);
            ImmutableList<ColumnIdentifier> dependantIds = maxInd.stream()
                    .map(idToUind::get)
                    .map(InclusionDependency::getDependant)
                    .map(ColumnPermutation::getColumnIdentifiers)
                    .flatMap(List::stream)
                    .collect(toImmutableList());
            ColumnPermutation dependant = new ColumnPermutation();
            dependant.setColumnIdentifiers(dependantIds);
            resultReceiver.receiveResult(new InclusionDependency(dependant, referenced));
        }
    }

    private List<ValuePositions> generateCoordinates(
            InclusionDependency unaryInd,
            ImmutableMap<ColumnIdentifier, TableInputGenerator> attributes,
            ImmutableMap<String, ColumnIdentifier> indexColumns) throws AlgorithmExecutionException {
        List<ValuePositions> valuePositions = new ArrayList<>();
        ColumnIdentifier lhs = getUnaryIdentifier(unaryInd.getDependant());
        ColumnIdentifier rhs = getUnaryIdentifier(unaryInd.getReferenced());

        RelationalInput inputA = config.getDataAccessObject()
                .getSortedRelationalInput(attributes.get(lhs), lhs, getIndexColumn(indexColumns, lhs), config.getIndexColumn(), false);
        RelationalInput inputB = config.getDataAccessObject().
                getSortedRelationalInput(attributes.get(rhs), rhs, getIndexColumn(indexColumns, rhs), config.getIndexColumn(), false);
        AttributeIterator cursorA =  new AttributeIterator(inputA, lhs, config.getIndexColumn());
        AttributeIterator cursorB = new AttributeIterator(inputB, rhs, config.getIndexColumn());
        String previousValue = null;

        cursorA.next();
        cursorB.next();

        while (cursorA.hasNext() || cursorB.hasNext()) {
            AttributeValuePosition valA = cursorA.current();
            AttributeValuePosition valB = cursorB.current();
            previousValue = valA.getValue();

            if (valA.getValue().equals(valB.getValue())) {
                IntList positionsA = new IntArrayList();
                IntList positionsB = new IntArrayList();

                AttributeValuePosition nextValA = cursorA.current();
                while (nextValA.getValue().equals(valA.getValue())) {
                    positionsA.add(nextValA.getPosition());
                    if (!cursorA.hasNext()) {
                        break;
                    }
                    nextValA = cursorA.next();
                }

                AttributeValuePosition nextValB = cursorB.current();
                while (nextValB.getValue().equals(valB.getValue())) {
                    positionsB.add(nextValB.getPosition());
                    if (!cursorB.hasNext()) {
                        break;
                    }
                    nextValB = cursorB.next();
                }

                if (positionsA.size() > 0 && positionsB.size() > 0) {
                    valuePositions.add(new ValuePositions(positionsA, positionsB));
                }
            } else if (valA.getValue().compareTo(valB.getValue()) < 0) {
                if (!cursorA.hasNext()) {
                    break;
                }
                cursorA.next();
            } else {
                if (!cursorB.hasNext()) {
                    break;
                }
                cursorB.next();
            }
        }
        if (!cursorA.current().getValue().equals(previousValue) &&
                cursorA.current().getValue().equals(cursorB.current().getValue())) {
            valuePositions.add(new ValuePositions(cursorA.current().getPosition(), cursorB.current().getPosition()));
        }
        cursorA.close();
        cursorB.close();
        return valuePositions;
    }

    private ImmutableMap<ColumnIdentifier, TableInputGenerator> getRelationalInputMap(
            ImmutableList<TableInputGenerator> inputGenerators) throws InputGenerationException {
        Map<ColumnIdentifier, TableInputGenerator> relationalInputs = new HashMap<>();
        for (TableInputGenerator generator : inputGenerators) {
            try (RelationalInput input = generator.generateNewCopy()) {
                input.columnNames()
                        .forEach(columnName -> relationalInputs.put(
                                new ColumnIdentifier(input.relationName(), columnName), generator));
            } catch (Exception e) {
                throw new InputGenerationException(format("Error getting copy of %s", generator), e);
            }
        }
        return ImmutableMap.copyOf(relationalInputs);
    }

    private ImmutableMap<String, ColumnIdentifier> getIndexColumns(Map<ColumnIdentifier, TableInputGenerator> relationalInputs) {
        Map<String, ColumnIdentifier> indexColumns = new HashMap<>();
        ImmutableList<ColumnIdentifier> columns = relationalInputs.keySet().stream().sorted().collect(toImmutableList());
        for (ColumnIdentifier columnIdentifier : columns) {
            if (indexColumns.containsKey(columnIdentifier.getTableIdentifier())) {
                continue;
            }
            indexColumns.put(columnIdentifier.getTableIdentifier(), columnIdentifier);
        }
        return ImmutableMap.copyOf(indexColumns);
    }

    private ColumnIdentifier getIndexColumn(Map<String, ColumnIdentifier> indexColumns, ColumnIdentifier column)
            throws AlgorithmExecutionException {
        if (!indexColumns.containsKey(column.getTableIdentifier())) {
            throw new AlgorithmExecutionException(
                    format("Cannot find table %s in index columns", column.getTableIdentifier()));
        }
        return indexColumns.get(column.getTableIdentifier());
    }

    private Path getPath() throws FileCreationException {
        return config.getTempFileGenerator().getTemporaryFile().toPath();
    }

    private ColumnIdentifier getUnaryIdentifier(ColumnPermutation columnPermutation) {
        return getOnlyElement(columnPermutation.getColumnIdentifiers());
    }

    private void writeToFile(List<ValuePositions> uindCoordinates, Path path)
            throws IOException {
        Map<Integer, List<Integer>> positions = new HashMap<>();
        for (ValuePositions valPos : uindCoordinates) {
            for (int lhs : valPos.getPositionsA()) {
                positions.put(lhs, valPos.getPositionsB());
            }
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path, UTF_8)) {
            for (int lhs : positions.keySet().stream().sorted().collect(toList())) {
                List<Integer> valuePositions = positions.get(lhs);
                writer.write(UindCoordinates.toLine(lhs, valuePositions));
                writer.newLine();
            }
        }
    }

    // TODO: Debugging only, remove
    public IntSet cachedStoreUindCoordinates() {
        uindToPath.put(0, Paths.get("/home/ntzr/mind2-scop-tmp/Mind2Algorithm4197031579466151552tmp"));
        uindToPath.put(1, Paths.get("/home/ntzr/mind2-scop-tmp/Mind2Algorithm5917111378669304947tmp"));
        uindToPath.put(2, Paths.get("/home/ntzr/mind2-scop-tmp/Mind2Algorithm6413565627858387043tmp"));
        uindToPath.put(3, Paths.get("/home/ntzr/mind2-scop-tmp/Mind2Algorithm3512138551421949066tmp"));
        uindToPath.put(4, Paths.get("/home/ntzr/mind2-scop-tmp/Mind2Algorithm3806585669409253463tmp"));
        uindToPath.put(5, Paths.get("/home/ntzr/mind2-scop-tmp/Mind2Algorithm7738645437290930012tmp"));
        uindToPath.put(6, Paths.get("/home/ntzr/mind2-scop-tmp/Mind2Algorithm6064171548428247053tmp"));
        uindToPath.put(7, Paths.get("/home/ntzr/mind2-scop-tmp/Mind2Algorithm4201991446690561848tmp"));
        uindToPath.put(8, Paths.get("/home/ntzr/mind2-scop-tmp/Mind2Algorithm7155422949762178868tmp"));
        uindToPath.put(9, Paths.get("/home/ntzr/mind2-scop-tmp/Mind2Algorithm1975003286451848672tmp"));

        ImmutableList<InclusionDependency> sortedUinds = uinds.stream()
                .sorted(Comparator.comparing(InclusionDependency::toString))
                .collect(toImmutableList());
        for (int i = 0; i < sortedUinds.size(); i++) {
            idToUind.put(i, sortedUinds.get(i));
        }
        return new IntOpenHashSet(idToUind.keySet());
    }
}
