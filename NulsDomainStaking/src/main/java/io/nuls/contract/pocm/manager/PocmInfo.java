/**
 * MIT License
 * <p>
 * Copyright (c) 2017-2018 nuls.io
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.nuls.contract.pocm.manager;

import io.nuls.contract.pocm.util.CandyToken;
import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Block;
import io.nuls.contract.sdk.Msg;

import java.math.BigInteger;

/**
 * @author: PierreLuo
 * @date: 2021/9/9
 */
public class PocmInfo {

    //1天=24*60*60秒
    public final long TIMEPERDAY = 86400;
    public BigInteger _2000_NULS = BigInteger.valueOf(200000000000L);
    public final BigInteger _1e12 = BigInteger.TEN.pow(12);
    public CandyToken candyTokenWrapper;
    public Address candyToken; // Address of candy token contract.
    public int candyAssetChainId;// chainId of candy token contract.
    public int candyAssetId;// assetId of candy token contract.
    public boolean isNRC20Candy;//糖果是否是NRC20资产
    public Long lastRewardBlock;  // Last block number that token distribution occurs.
    public BigInteger accPerShare;    // Accumulated token per share, times 1e12. See below.
    public BigInteger candyPerBlock;
    private BigInteger lpSupply = BigInteger.ZERO; // 抵押总量
    public BigInteger candySupply;// 糖果发行总量
    public int lockedTokenDay;// 获取Token奖励的锁定天数
    public long lockedTime;
    public BigInteger minimumStaking;// 最低质押na数量(1亿个na等于1个NULS）
    public BigInteger maximumStaking;// 最高质押na数量(1亿个na等于1个NULS）
    public boolean openConsensus = false;//是否开启合约共识功能
    public boolean openAwardConsensusNodeProvider = false;//是否奖励共识节点提供者
    public String authorizationCode;//dapp的唯一识别码
    public Long endBlock;// 池子结束高度
    public BigInteger c = BigInteger.TEN;
    public int operatingModel;
    public int rewardDrawRatioForLp;

    public void addLpSupply(BigInteger lpSupply) {
        if (this.lpSupply.compareTo(BigInteger.ZERO) == 0 && lpSupply.compareTo(BigInteger.ZERO) > 0) {
            BigInteger candyBalance = candyTokenWrapper.balanceOf(Msg.address());
            BigInteger blockCount = candyBalance.divide(candyPerBlock);
            this.endBlock = Block.number() + blockCount.longValue();
        }
        this.lpSupply = this.lpSupply.add(lpSupply);
    }

    public void subLpSupply(BigInteger lpSupply) {
        this.lpSupply = this.lpSupply.subtract(lpSupply);
        if (this.lpSupply.compareTo(BigInteger.ZERO) == 0) {
            BigInteger candyBalance = candyTokenWrapper.balanceOf(Msg.address());
            if (candyBalance.compareTo(BigInteger.ZERO) == 0) {
                this.endBlock = Block.number();
            } else {
                // time: 127174492800 is 6000-01-01, blockCount = time/10 = 12717449280
                this.endBlock = Block.number() + 12717449280L;
            }
        }
    }

    public BigInteger getLpSupply() {
        return this.lpSupply;
    }
}
