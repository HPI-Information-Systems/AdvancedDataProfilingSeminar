package de.metanome.algorithms.zigzag;

import static de.metanome.algorithms.zigzag.configuration.ConfigurationKey.EPSILON;
import static de.metanome.algorithms.zigzag.configuration.ConfigurationKey.K;

import de.metanome.algorithm_integration.AlgorithmConfigurationException;
import de.metanome.algorithm_integration.AlgorithmExecutionException;
import de.metanome.algorithm_integration.algorithm_types.InclusionDependencyAlgorithm;
import de.metanome.algorithm_integration.algorithm_types.IntegerParameterAlgorithm;
import de.metanome.algorithm_integration.configuration.ConfigurationRequirement;
import de.metanome.algorithm_integration.configuration.ConfigurationRequirementInteger;
import de.metanome.algorithm_integration.result_receiver.InclusionDependencyResultReceiver;
import de.metanome.algorithm_integration.results.InclusionDependency;
import de.metanome.algorithms.zigzag.configuration.ZigzagConfiguration;
import de.metanome.algorithms.zigzag.configuration.ZigzagConfiguration.ZigzagConfigurationBuilder;
import de.metanome.input.ind.AlgorithmType;
import de.metanome.input.ind.InclusionDependencyInput;
import de.metanome.input.ind.InclusionDependencyInputConfigurationRequirements;
import de.metanome.input.ind.InclusionDependencyInputGenerator;
import de.metanome.input.ind.InclusionDependencyInputParameterAlgorithm;
import de.metanome.input.ind.InclusionDependencyParameters;
import de.metanome.validation.InclusionDependencyValidationAlgorithm;
import de.metanome.validation.ValidationConfigurationRequirements;
import de.metanome.validation.ValidationParameters;
import de.metanome.validation.database.QueryType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class ZigzagAlgorithm implements InclusionDependencyAlgorithm,
    IntegerParameterAlgorithm,
    InclusionDependencyInputParameterAlgorithm,
    InclusionDependencyValidationAlgorithm {

  private final ZigzagConfigurationBuilder configurationBuilder;
  final ValidationParameters validationParameters;
  final InclusionDependencyParameters indInputParams = new InclusionDependencyParameters();

  public ZigzagAlgorithm() {
    configurationBuilder = ZigzagConfiguration.builder();
    validationParameters = new ValidationParameters();
  }

  List<ConfigurationRequirement<?>> common() {
    final List<ConfigurationRequirement<?>> requirements = new ArrayList<>();
    requirements.add(new ConfigurationRequirementInteger(K.name()));
    requirements.add(new ConfigurationRequirementInteger(EPSILON.name()));
    requirements.addAll(InclusionDependencyInputConfigurationRequirements.indInput());
    requirements.addAll(ValidationConfigurationRequirements.validationStrategy());
    return requirements;
  }

  @Override
  public void setIntegerConfigurationValue(String identifier, Integer... values)
      throws AlgorithmConfigurationException {
    if (identifier.equals(K.name())) {
      configurationBuilder.k(values[0]);
    } else if (identifier.equals(EPSILON.name())) {
      configurationBuilder.epsilon(values[0]);
    }
  }

  @Override
  public void setListBoxConfigurationValue(String identifier, String... selectedValues)
      throws AlgorithmConfigurationException {
    InclusionDependencyInputConfigurationRequirements.acceptListBox(identifier, selectedValues,
        indInputParams);
    ValidationConfigurationRequirements
        .acceptListBox(identifier, selectedValues, validationParameters);
  }

  @Override
  public void setStringConfigurationValue(String identifier, String... values)
      throws AlgorithmConfigurationException {
    InclusionDependencyInputConfigurationRequirements.acceptString(identifier, values,
        indInputParams);
  }

  @Override
  public void setResultReceiver(InclusionDependencyResultReceiver resultReceiver) {
    configurationBuilder.resultReceiver(resultReceiver);
  }

  @Override
  public void execute() throws AlgorithmExecutionException {
    // Hardcode for now until this works with metanome-cli
    indInputParams.setAlgorithmType(AlgorithmType.DE_MARCHI);
    validationParameters.setQueryType(QueryType.ERROR_MARGIN);

    InclusionDependencyInput uindInput = new InclusionDependencyInputGenerator().get(indInputParams);
    Set<InclusionDependency> uinds = new HashSet<>(uindInput.execute());
    ZigzagConfiguration configuration = configurationBuilder
        .unaryInds(uinds)
        .validationParameters(validationParameters)
        .build();
    new Zigzag(configuration).execute();
  }

  @Override
  public String getAuthors() {
    return "Fabian Windheuser, Nils Strelow";
  }

  @Override
  public String getDescription() {
    return "Implementation of 'Zigzag : a new algorithm for discovering large inclusion dependencies in relational databases' by De Marchi, Petit, 2003";
  }
}