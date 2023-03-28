package com.aquila.chess.strategy.mcts;

import com.aquila.chess.MCTSStrategyConfig;
import com.aquila.chess.utils.Utils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

@Slf4j
public class CacheValues {

    private final Map<Long, CacheValue> lruMap;

    public Collection<CacheValue> getValues() {
        return lruMap.values();
    }

    public CacheValues(final int size) {
        lruMap = new LRUMap<>(size);
    }

    public synchronized void clearCache() {
        if (log.isDebugEnabled()) log.debug("EMPTY cacheNNValues: {}", this.lruMap.size());
        this.lruMap.clear();
    }

    public synchronized CacheValue get(final long key) {
        CacheValue ret = lruMap.get(key);
        return ret;
    }

    public synchronized boolean containsKey(final long key) {
        return this.lruMap.containsKey(key);
    }

    public synchronized int size() {
        return this.lruMap.size();
    }

    public synchronized CacheValue create(long key, final String label) {
        if (containsKey(key)) throw new RuntimeException("node already created for key:" + key);
        CacheValue ret = CacheValue.getNotInitialized(String.format("[%d] %s", key, label));
        this.lruMap.put(key, ret);
        return ret;
    }

    public synchronized CacheValue updateValueAndPolicies(long key, float value, float[] notNormalisedPolicies) {
        CacheValue cacheValue = this.lruMap.get(key);
        if (cacheValue == null) {
            throw new RuntimeException("node for key:" + key + " not found");
        }
        cacheValue.setTrueValuesAndPolicies(value, notNormalisedPolicies);
        return cacheValue;
    }

    @Getter
    static public class CacheValue extends OutputNN implements Serializable {

        private static final float NOT_INITIALIZED_VALUE = 0;

        private static final CacheValue getNotInitialized(final String label) {
            return new CacheValue(NOT_INITIALIZED_VALUE, label, new float[PolicyUtils.MAX_POLICY_INDEX]);
        }

        @Setter
        private boolean initialised = false;

        @Setter
        private boolean propagated = false;

        final private String label;

        private MCTSNode node = null;

        private CacheValueType type = CacheValueType.INTERMEDIATE;

        private int nbPropagate = 1;

        private CacheValue(float value, String label, float[] policies) {
            super(value, policies);
            this.label = label;
        }

        public String toString() {
            return ToStringBuilder.reflectionToString(this, ToStringStyle.MULTI_LINE_STYLE);
        }

        public void resetNbPropragate() {
            nbPropagate = 0;
        }

        public void setAsRoot() {
            this.type = CacheValueType.ROOT;
        }

        public synchronized void normalise(float[] policies) {
            int[] indexes = PolicyUtils.getIndexesFilteredPolicies(node.getChildMoves());
            if (log.isDebugEnabled())
                log.debug("NORMALIZED type:{} move.size:{} dirichlet:{}", this.type, node.getChildMoves().size(), node.isDirichlet());
            boolean isDirichlet =node.getState() == MCTSNode.State.ROOT;
            isDirichlet = MCTSStrategyConfig.isDirichlet(node.getMove()) && isDirichlet;
            float[] normalisedPolicies = Utils.toDistribution(policies, indexes, isDirichlet);
//            if (Arrays.stream(normalisedPolicies).filter(policy -> Double.isNaN(policy)).count() > 0) {
//                throw new RuntimeException("ERROR, some policy with NaN value");
//            }
            this.policies = normalisedPolicies;
        }

        public void setAsLeaf() {
            this.type = CacheValueType.LEAF;
        }

        public OutputNN setTrueValuesAndPolicies(final float value, final float[] policies) {
            this.value = value;
            this.policies = policies;
            log.debug("setTrueValuesAndPolicies({},{} {} {} ..)", value, policies[0], policies[1], policies[2]);
            this.setInitialised(true);
            if (node != null) {
                setTrueValuesAndPolicies();
            }
            return this;
        }

        public void setTrueValuesAndPolicies() {
            if (initialised && type != CacheValueType.LEAF) {
                node.syncSum();
                log.debug("normalise: {} {} {}", policies[0], policies[1], policies[2]);
                normalise(policies);
            }
        }

        public void setNode(MCTSNode node) {
            this.node = node;
            setTrueValuesAndPolicies();
        }

        public static enum CacheValueType {
            INTERMEDIATE, ROOT, LEAF
        }

        public void incPropagate() {
            this.nbPropagate++;
        }

    }
}
