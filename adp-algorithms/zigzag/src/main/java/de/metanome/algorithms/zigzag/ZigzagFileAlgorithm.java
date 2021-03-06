package de.metanome.algorithms.zigzag;

import static de.metanome.algorithms.zigzag.configuration.ConfigurationKey.TABLE;
import static java.util.Arrays.asList;

import de.metanome.algorithm_integration.algorithm_types.RelationalInputParameterAlgorithm;
import de.metanome.algorithm_integration.configuration.ConfigurationRequirement;
import de.metanome.algorithm_integration.configuration.ConfigurationRequirementRelationalInput;
import de.metanome.algorithm_integration.input.RelationalInputGenerator;
import de.metanome.algorithms.zigzag.configuration.ZigzagConfiguration;
import de.metanome.algorithms.zigzag.configuration.ZigzagConfiguration.ZigzagConfigurationBuilder;
import de.metanome.input.ind.InclusionDependencyInputConfigurationRequirements;
import java.util.ArrayList;
import java.util.List;

public class ZigzagFileAlgorithm extends ZigzagAlgorithm implements
    RelationalInputParameterAlgorithm {

  // DISCLAIMER Not working, since Zigzag uses validation-strategy, which only works for databases
  // TODO Adapt validation-strategy to work with RelationalInput

  private final ZigzagConfigurationBuilder configurationBuilder;

  public ZigzagFileAlgorithm() {
    configurationBuilder = ZigzagConfiguration.builder();
  }

  @Override
  public ArrayList<ConfigurationRequirement<?>> getConfigurationRequirements() {
    final ArrayList<ConfigurationRequirement<?>> requirements = new ArrayList<>();
    requirements.addAll(common());
    requirements.addAll(file());
    return requirements;
  }

  private List<ConfigurationRequirement<?>> file() {
    final List<ConfigurationRequirement<?>> requirements = new ArrayList<>();
    requirements.add(new ConfigurationRequirementRelationalInput(TABLE.name()));
    return requirements;
  }

  @Override
  public void setRelationalInputConfigurationValue(String identifier,
      RelationalInputGenerator... values) {
    if (identifier.equals(TABLE.name())) {
      configurationBuilder.relationalInputGenerators(asList(values));
      InclusionDependencyInputConfigurationRequirements
          .acceptRelationalInputGenerators(values, indInputParams);
    }
  }
}