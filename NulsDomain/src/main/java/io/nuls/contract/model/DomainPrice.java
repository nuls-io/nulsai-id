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
package io.nuls.contract.model;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: PierreLuo
 * @date: 2024/11/28
 */
public class DomainPrice {
    private Map<Integer, BigInteger> domainPrice;
    private BigInteger defaultPrice;
    private int defaultPriceLength;

    public DomainPrice(BigInteger ONE_NULS) {
        domainPrice = new HashMap<Integer, BigInteger>();
        domainPrice.put(1, ONE_NULS.multiply(BigInteger.valueOf(10000)));
        domainPrice.put(2, ONE_NULS.multiply(BigInteger.valueOf(5000)));
        domainPrice.put(3, ONE_NULS.multiply(BigInteger.valueOf(1000)));
        domainPrice.put(4, ONE_NULS.multiply(BigInteger.valueOf(120)));
        domainPrice.put(5, ONE_NULS.multiply(BigInteger.valueOf(110)));
        this.defaultPrice = ONE_NULS.multiply(BigInteger.valueOf(100));
        this.defaultPriceLength = 6;
    }

    public Map<Integer, BigInteger> getDomainPrice() {
        return domainPrice;
    }

    public BigInteger getDefaultPrice() {
        return defaultPrice;
    }

    public void setDefaultPrice(BigInteger defaultPrice) {
        this.defaultPrice = defaultPrice;
    }

    public int getDefaultPriceLength() {
        return defaultPriceLength;
    }

    public void setDefaultPriceLength(int defaultPriceLength) {
        this.defaultPriceLength = defaultPriceLength;
    }
}
