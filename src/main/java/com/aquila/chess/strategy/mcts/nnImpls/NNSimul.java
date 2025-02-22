package com.aquila.chess.strategy.mcts.nnImpls;

import com.aquila.chess.strategy.mcts.OutputNN;
import com.aquila.chess.strategy.mcts.UpdateLr;
import com.aquila.chess.strategy.mcts.utils.PolicyUtils;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.api.NeuralNetwork;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class NNSimul extends NNConstants {

    public NNSimul(long seed) {
        super(seed);
    }

    @Override
    public synchronized List<OutputNN> outputs(double[][][][] nbIn, int len) {
        List<OutputNN> ret = new ArrayList<>();
        for (int i = 0; i < len; i++) {
            double value;
            double[] policies = new double[PolicyUtils.MAX_POLICY_INDEX];
            if (randomGenerator.nextFloat() > 0.5) {
                value = mediumValue + (-0.000001F + 0.000002F * randomGenerator.nextFloat());
                for (int policyIndex = 0; policyIndex < PolicyUtils.MAX_POLICY_INDEX; policyIndex++) {
                    policies[policyIndex] = mediumPolicies + (-0.000001F + 0.000002F * randomGenerator.nextFloat());
                    if (offsets.containsKey(policyIndex)) {
                        double offset = offsets.get(policyIndex);
                        if(log.isDebugEnabled()) log.debug("add {} to policyIndex:{} move:{}", offset, policyIndex, PolicyUtils.moveFromIndex(policyIndex));
                        policies[policyIndex] += offset;
                    }
                }
            } else {
                value = mediumValue;
                for (int policyIndex = 0; policyIndex < PolicyUtils.MAX_POLICY_INDEX; policyIndex++) {
                    policies[policyIndex] = mediumPolicies;
                    if (offsets.containsKey(policyIndex)) {
                        double offset = offsets.get(policyIndex);
                        if(log.isDebugEnabled()) log.debug("add {} to policyIndex:{} move:{}", offset, policyIndex, PolicyUtils.moveFromIndex(policyIndex));
                        policies[policyIndex] += offset;
                    }
                }
            }
            if(log.isDebugEnabled()) {
                double sumPolicies = Arrays.stream(policies).sum();
                log.debug("[{}] -> value:{} sumPolicies:{}", i, value, sumPolicies);
            }
            ret.add(new OutputNN(value, policies));
        }
        return ret;
    }

    @Override
    public double getScore() {
        return 0;
    }

    @Override
    public void setUpdateLr(UpdateLr updateLr, int nbGames) {

    }

    @Override
    public void updateLr(int nbGames) {

    }

    @Override
    public void save() throws IOException {

    }

    @Override
    public void fit(double[][][][] inputs, double[][] policies, double[][] values) {
        log.info("FIT SIMULATED [{}]", inputs.length);
    }

    @Override
    public String getFilename() {
        return null;
    }

    @Override
    public double getLR() {
        return 0;
    }

    @Override
    public void setLR(double lr) {

    }

    @Override
    public NeuralNetwork getNetwork() {
        return null;
    }

    public void addIndexOffset(double offset, int... indexes) {
        for (int index : indexes) {
            this.offsets.put(index, offset);
        }
    }

    public void clearIndexOffset() {
        this.offsets.clear();
    }

}
