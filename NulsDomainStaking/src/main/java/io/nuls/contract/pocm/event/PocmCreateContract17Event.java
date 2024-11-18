package io.nuls.contract.pocm.event;

import io.nuls.contract.sdk.Event;

import java.math.BigInteger;

public class PocmCreateContract17Event implements Event {
    private String tokenAddress;
    private int candyAssetChainId;
    private int candyAssetId;
    private BigInteger candyPerBlock;
    private BigInteger candySupply;
    private int lockedTokenDay;
    private BigInteger minimumStaking;
    private BigInteger maximumStaking;
    private boolean openConsensus;
    boolean openAwardConsensusNodeProvider;
    private String authorizationCode;
    private int operatingModel;
    private int rewardDrawRatioForLp;

    public PocmCreateContract17Event() {
    }

    public PocmCreateContract17Event(String tokenAddress, int candyAssetChainId, int candyAssetId, BigInteger candyPerBlock, BigInteger candySupply, int lockedTokenDay, BigInteger minimumStaking, BigInteger maximumStaking, boolean openConsensus, boolean openAwardConsensusNodeProvider, String authorizationCode, int operatingModel, int rewardDrawRatioForLp) {
        this.tokenAddress = tokenAddress;
        this.candyAssetChainId = candyAssetChainId;
        this.candyAssetId = candyAssetId;
        this.candyPerBlock = candyPerBlock;
        this.candySupply = candySupply;
        this.lockedTokenDay = lockedTokenDay;
        this.minimumStaking = minimumStaking;
        this.maximumStaking = maximumStaking;
        this.openConsensus = openConsensus;
        this.openAwardConsensusNodeProvider = openAwardConsensusNodeProvider;
        this.authorizationCode = authorizationCode;
        this.operatingModel = operatingModel;
        this.rewardDrawRatioForLp = rewardDrawRatioForLp;
    }

}
