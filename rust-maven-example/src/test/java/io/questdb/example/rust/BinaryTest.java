/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2023 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.example.rust;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class BinaryTest {

    @Test
    public void testBinary() throws Exception {
        File binaryFile = new File("target/bin/str-reverse-binary");

        Process process = new ProcessBuilder(
                Arrays.asList(
                        binaryFile.getAbsolutePath(),
                        "Hello World!"))
                .start();

        List<String> output = new BufferedReader(new InputStreamReader(process.getInputStream()))
                .lines()
                .collect(Collectors.toList());

        assertEquals(0, process.waitFor());
        final List<String> exp = new ArrayList<>();
        Collections.addAll(exp,
                ">>>>>>>>>>>>>>>>>>>>>>>>>",
                "!dlroW olleH",
                "<<<<<<<<<<<<<<<<<<<<<<<<<<");
        assertEquals(exp, output);
    }
}
