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
package io.nuls.contract.pocm.util;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author: PierreLuo
 * @date: 2021/8/31
 */
public class PocmUtil {

    public final static int NORMAL_MODE = 0;
    public final static int LP_MODE = 1;
    public final static BigInteger TEN_THOUSAND = BigInteger.valueOf(10000);
    public final static BigInteger MININUM_TRANSFER_AMOUNT = BigInteger.TEN.pow(6);

    public final static BigInteger ONE_NULS = BigInteger.valueOf(100000000L);
    //90%
    public final static BigDecimal AVAILABLE_PERCENT = new BigDecimal("0.9");
    //100%
    public final static BigDecimal FULL_PERCENT = new BigDecimal("1");

    public static BigDecimal toNuls(BigInteger na) {
        return new BigDecimal(na).movePointLeft(8);
    }

    public static BigInteger extractDecimal(BigInteger na) {
        return na.subtract(toNuls(na).toBigInteger().multiply(ONE_NULS));
    }

}
