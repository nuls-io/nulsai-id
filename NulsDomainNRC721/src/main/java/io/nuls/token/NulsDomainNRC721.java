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
package io.nuls.token;

import io.nuls.contract.sdk.Address;
import io.nuls.contract.sdk.Contract;
import io.nuls.contract.sdk.Msg;
import io.nuls.contract.sdk.annotation.Required;
import io.nuls.token.base.NRC721MetadataBase;
import io.nuls.token.entity.Domain;

import java.math.BigInteger;

import static io.nuls.contract.sdk.Utils.require;
import static io.nuls.contract.sdk.Utils.revert;

/**
 * @author: PierreLuo
 * @date: 2019-06-10
 */
public class NulsDomainNRC721 extends NRC721MetadataBase implements Contract {

    private boolean initialized = false;
    private Address domain;

    public NulsDomainNRC721(String name, String symbol) {
        super.setName(name);
        super.setSymbol(symbol);
    }

    public void initialize(
            Address official,
            Address domain
        ) {
        onlyOwner();
        if (initialized) {
            revert("DomainInitialized");
        }
        initialized = true;
        super.setOfficial(official);
        this.domain = domain;
        super.addMinter(domain);
        super.renounceMinter();
    }

    public boolean mint(@Required Address to, @Required BigInteger tokenId) {
        onlyMinter();
        super.mintWithTokenURIBase(to, tokenId, null);
        return true;
    }

    public boolean mintWithTokenURI(@Required Address to, @Required BigInteger tokenId, String tokenURI) {
        onlyMinter();
        super.mintWithTokenURIBase(to, tokenId, tokenURI);
        return true;
    }

    @Override
    protected void transferFromBase(Address from, Address to, BigInteger tokenId) {
        super.transferFromBase(from, to, tokenId);
        Domain _domain = new Domain(domain);
        _domain.domainTransfer(from, to, tokenId);
    }

    @Override
    public void setTokenURI(BigInteger tokenId, String uri) {
        onlyMinter();
        super.setTokenURI(tokenId, uri);
    }

    public void burn(@Required Address owner, @Required BigInteger tokenId) {
        onlyOfficial();
        require(isApprovedOrOwner(Msg.sender(), tokenId), "NRC721: transfer caller is not owner nor approved");
        super.burnBase(owner, tokenId);
    }

    public boolean batchMint(@Required String[] tos, @Required String[] tokenIds) {
        onlyOfficial();
        require(tos.length <= 100, "max size: 100.");
        require(tos.length == tokenIds.length, "array size error.");
        for (int i = 0;i<tos.length;i++) {
            this.mintWithTokenURIBase(new Address(tos[i]), new BigInteger(tokenIds[i]), null);
        }
        return true;
    }

    public boolean batchMintWithTokenURI(@Required String[] tos, @Required String[] tokenIds, @Required String[] tokenURIs) {
        onlyOfficial();
        require(tos.length <= 100, "max size: 100.");
        require(tos.length == tokenIds.length && tokenIds.length == tokenURIs.length, "array size error.");
        for (int i = 0;i<tos.length;i++) {
            this.mintWithTokenURIBase(new Address(tos[i]), new BigInteger(tokenIds[i]), tokenURIs[i]);
        }
        return true;
    }

    public void changeMain(Address _main) {
        onlyOfficial();
        this.domain = _main;
    }

}
