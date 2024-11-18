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
package io.nuls.contract.entity;

import io.nuls.contract.sdk.Address;

import java.math.BigInteger;

import static io.nuls.contract.sdk.Utils.require;

/**
 * @author: PierreLuo
 * @date: 2024/11/14
 */
public class Staking {
    private Address contract;

    public Staking(Address addr) {
        require(addr.isContract(), "not contract address");
        this.contract = addr;
    }

    public void depositForOwn(BigInteger value) {
        contract.callWithReturnValue("depositForOwn", "", null, value);
    }

    public void transferConsensusRewardByOwner() {
        contract.callWithReturnValue("transferConsensusRewardByOwner", "", null, BigInteger.ZERO);
    }

    public void addOtherAgent(String agentHash) {
        String[][] args = new String[1][];
        args[0] = new String[]{agentHash};
        contract.callWithReturnValue("addOtherAgent", "", args, BigInteger.ZERO);
    }

    public void withdraw(BigInteger _amount) {
        String[][] args = new String[1][];
        args[0] = new String[]{_amount.toString()};
        contract.callWithReturnValue("withdraw", "", args, BigInteger.ZERO);
    }

    public void quit() {
        String[][] args = new String[1][];
        args[0] = new String[]{"u"};
        contract.callWithReturnValue("quit", "", args, BigInteger.ZERO);
    }

    public BigInteger ownerTotalConsensusAward() {
        String result = contract.callWithReturnValue("ownerTotalConsensusAward", "", null, BigInteger.ZERO);
        if (result != null && result.length() > 0) {
            return new BigInteger(result);
        }
        return BigInteger.ZERO;
    }

    public BigInteger ownerAvailableConsensusAward() {
        String result = contract.callWithReturnValue("ownerAvailableConsensusAward", "", null, BigInteger.ZERO);
        if (result != null && result.length() > 0) {
            return new BigInteger(result);
        }
        return BigInteger.ZERO;
    }

}
