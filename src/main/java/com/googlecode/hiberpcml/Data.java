/*
 * The MIT License
 *
 * Copyright 2011 John Arevalo <johnarevalo@gmail.com>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.googlecode.hiberpcml;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks a field as a significant element for pcml invocation.
 * @author John Arevalo <johnarevalo@gmail.com>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Data {

    /**
     * 
     * @return name given to this array in the .pcml file
     */
    String pcmlName();

    /**
     * Fill the param with this String.
     * @return String used when it is necesary fill the param with fixed length
     */
    String completeWith() default "";

    /**
     * This param is used to fill this value with {@link #completeWith()}
     * @return max length for this data.
     */
    int length() default 0;

    /**
     * Specifies the usage of this param in the pcml. 
     * Default value is {@link UsageType#INPUTOUTPUT}
     * @return Usage of this param. 
     * @see UsageType
     */
    public UsageType usage() default UsageType.INPUTOUTPUT;
}
