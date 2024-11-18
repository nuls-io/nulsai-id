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
import io.nuls.contract.sdk.annotation.Required;

import java.math.BigInteger;

import static io.nuls.contract.sdk.Utils.require;
import static io.nuls.contract.sdk.Utils.revert;

/**
 * @author: PierreLuo
 * @date: 2024/11/18
 */
public class NRC721 {
    private Address contract;

    public NRC721(Address addr) {
        require(addr.isContract(), "not contract address");
        this.contract = addr;
    }

    public void mintWithTokenURI(Address to, BigInteger tokenId, String tokenURI) {
        String[][] args = new String[3][];
        args[0] = new String[]{to.toString()};
        args[1] = new String[]{tokenId.toString()};
        args[2] = new String[]{tokenURI == null ? "" : tokenURI};
        contract.callWithReturnValue("mintWithTokenURI", "", args, BigInteger.ZERO);
    }

    public void setTokenURI(BigInteger tokenId, String uri) {
        String[][] args = new String[2][];
        args[0] = new String[]{tokenId.toString()};
        args[1] = new String[]{uri == null ? "" : uri};
        contract.callWithReturnValue("setTokenURI", "", args, BigInteger.ZERO);
    }

    public Address ownerOf(BigInteger tokenId) {
        String[][] args = new String[1][];
        args[0] = new String[]{tokenId.toString()};
        String result = contract.callWithReturnValue("ownerOf", "", args, BigInteger.ZERO);
        if (result != null && result.length() > 0) {
            return new Address(result);
        }
        revert("empty owner");
        return null;
    }

    public String name() {
        String result = contract.callWithReturnValue("name", "", null, BigInteger.ZERO);
        if (result != null && result.length() > 0) {
            return result;
        }
        return null;
    }
}
