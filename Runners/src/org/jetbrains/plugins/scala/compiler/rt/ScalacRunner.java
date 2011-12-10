/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.compiler.rt;

import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author ven
 */
public class ScalacRunner {
  public static final String SCALAC_QUALIFIED_NAME = "scala.tools.nsc.Main";

  public static void main(String[] args) {
    if (args.length != 1)
      throw new RuntimeException("ScalacRunner usage: ScalacRunner args_for_scalac_file");

    String fileName = args[0];
    File f = new File(fileName);
    if (!f.exists())
      throw new RuntimeException("ScalacRunner: args_for_scalac_file not found");

    List<String> scalacArgs = new ArrayList<String>();
    BufferedReader reader = null;
    FileInputStream inputStream = null;
    try {
      inputStream = new FileInputStream(f);
      reader = new BufferedReader(new InputStreamReader(inputStream));
      String line;
      do {
        line = reader.readLine();
        if (line != null) scalacArgs.add(line);
      } while (line != null);
    } catch (IOException e) {
      throw new RuntimeException("Scalac internal error", e);
    } finally {
      try {
        if (reader != null) try {
          reader.close();
        } catch (IOException e) {
          // do nothing
        }
      } finally {
        f.delete();
      }
    }

    try {
      Class<?> scalacMain = Class.forName(SCALAC_QUALIFIED_NAME);
      Method method = scalacMain.getMethod("main", String[].class);
      method.invoke(null, ((Object) scalacArgs.toArray(new String[scalacArgs.size()])));
    } catch (Throwable e) {
      throw new RuntimeException("Compiler classes not found: ", e);
    }
  }
}