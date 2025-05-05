/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.client.models;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * Model representing a survey response option
 */
@Setter
@Getter
public class ResponseData {

    @SerializedName("text")
    private String text;

    @SerializedName("value")
    private Integer value;

    @SerializedName("sequenceNo")
    private Integer sequenceNo;

    public ResponseData text(String text) {
        this.text = text;
        return this;
    }

    public ResponseData value(Integer value) {
        this.value = value;
        return this;
    }

    public ResponseData sequenceNo(Integer sequenceNo) {
        this.sequenceNo = sequenceNo;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResponseData that = (ResponseData) o;
        return Objects.equals(text, that.text) && Objects.equals(value, that.value) && Objects.equals(sequenceNo, that.sequenceNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, value, sequenceNo);
    }

    @Override
    public String toString() {
        return "ResponseData{" + "text='" + text + '\'' + ", value=" + value + ", sequenceNo=" + sequenceNo + '}';
    }
}
