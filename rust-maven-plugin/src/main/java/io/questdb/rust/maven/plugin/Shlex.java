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

package io.questdb.rust.maven.plugin;

import java.util.List;
import java.util.regex.Pattern;

public interface Shlex {

    /**
     * Escape a string for use in a shell command.
     *
     * @param s The string to escape.
     * @return The escaped string.
     * @see <a href="https://docs.python.org/3/library/shlex.html#shlex.quote">shlex.quote</a>
     */
    static String quote(String s) {
        if (s.isEmpty())
            return "''";
        Pattern unsafe = Pattern.compile("[^\\w@%+=:,./-]");
        if (unsafe.matcher(s).find())
            return "'" + s.replace("'", "'\"'\"'") + "'";
        else
            return s;
    }

    static String quote(List<String> args) {
        StringBuilder sb = new StringBuilder();
        for (String arg : args) {
            if (sb.length() > 0)
                sb.append(' ');
            sb.append(quote(arg));
        }
        return sb.toString();
    }
}
