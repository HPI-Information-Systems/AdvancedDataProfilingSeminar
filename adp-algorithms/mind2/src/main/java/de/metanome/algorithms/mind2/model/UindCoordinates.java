package de.metanome.algorithms.mind2.model;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import de.metanome.algorithm_integration.results.InclusionDependency;
import lombok.Data;

import java.util.Collection;
import java.util.stream.StreamSupport;

import static de.metanome.util.Collectors.toImmutableList;

@Data
public class UindCoordinates {

    public static final String FIELD_SEPERATOR = ";";
    public static final String ELEM_SEPERATOR = ",";

    public static UindCoordinates fromLine(InclusionDependency uind, String data) {
        ImmutableList<String> parts = ImmutableList.copyOf(Splitter.on(FIELD_SEPERATOR).trimResults().split(data));
        int lhsIndex = Integer.valueOf(parts.get(0));
        ImmutableList<Integer> rhsIndices = StreamSupport.stream(
                Splitter.on(ELEM_SEPERATOR).trimResults().split(parts.get(1)).spliterator(), false)
                .map(Integer::valueOf).collect(toImmutableList());
        return new UindCoordinates(uind, lhsIndex, rhsIndices);
    }

    private final InclusionDependency uind;
    private final Integer lhsIndex;
    private final ImmutableList<Integer> rhsIndices;

    public UindCoordinates(InclusionDependency uind, Integer lhsIndex, Collection<Integer> rhsIndices) {
        this.uind = uind;
        this.lhsIndex = lhsIndex;
        this.rhsIndices = rhsIndices.stream().sorted().collect(toImmutableList());
    }

    public String toLine() {
        return lhsIndex + FIELD_SEPERATOR + Joiner.on(ELEM_SEPERATOR).join(rhsIndices);
    }
}
