/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

public class ParamNameResolver {

  public static final String GENERIC_NAME_PREFIX = "param";

  /**
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * 键是索引，值是参数的名称
   * The name is obtained from {@link Param} if specified.
   * 如果param是指定的，那么就从param中获得名称
   * When {@link Param} is not specified, the parameter index is used.
   * 当params没有指定名称时，将使用参数索引。
   * Note that this index could be different from the actual index when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
   * 注意，当方法有特殊参数(例如 {@link RowBounds}或者{@link ResultHandler})时，此索引可能与实际索引不一致
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li> 指定参数名称的情况
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li> 未指定参数名称的情况
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li> 有特殊参数的情况
   * </ul>
   */
  private final SortedMap<Integer, String> names;

  private boolean hasParamAnnotation;

  public ParamNameResolver(Configuration config, Method method) {
    final Class<?>[] paramTypes = method.getParameterTypes();
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    final SortedMap<Integer, String> map = new TreeMap<>();
    int paramCount = paramAnnotations.length;
    // get names from @Param annotations
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters
        continue;
      }
      String name = null;
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        if (annotation instanceof Param) {
          hasParamAnnotation = true;
          name = ((Param) annotation).value();
          break;
        }
      }
      if (name == null) {
        // @Param was not specified.
        if (config.isUseActualParamName()) {
          name = getActualParamName(method, paramIndex);
        }
        if (name == null) {
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          name = String.valueOf(map.size());
        }
      }
      map.put(paramIndex, name);
    }
    names = Collections.unmodifiableSortedMap(map);
  }

  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }

  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * Returns parameter names referenced by SQL providers.
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * <p>
   * A single non-special parameter is returned without a name.
   * 返回一个没有名称的非特殊参数
   * Multiple parameters are named using the naming rule.
   * 使用命名规则给多个参数进行命名
   * In addition to the default names, this method also adds the generic names (param1, param2,
   * ...).
   * 除了默认名称之外，该方法也会添加泛型名称（param1, param2, ...）
   *
   * </p>
   */
  public Object getNamedParams(Object[] args) {
    //names 里面存储的是 {{0, "param1"}, {0, "param2"}} 参数索引位置和参数名
    final int paramCount = names.size();
    //所以如果参数或者参数名称列表为空，那么说明数据有问题，那么该值返回空
    if (args == null || paramCount == 0) {
      return null;
    } else if (!hasParamAnnotation && paramCount == 1) {
      //如果该方法的参数有@Param注解，并且参数总数只有1个，那么获取names中最低键值的key，然后从args参数中获取key位置的值。
      return args[names.firstKey()];
    } else { //否则，返回一个map数据，key=paramName, value = argsValue
      final Map<String, Object> param = new ParamMap<>();
      int i = 0;
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        //循环names数据，将paramName, argValue 放到param中
        param.put(entry.getValue(), args[entry.getKey()]);
        // add generic param names (param1, param2, ...)
        // 添加泛型参数名称（param1, param2, ...）
        final String genericParamName = GENERIC_NAME_PREFIX + (i + 1);
        // ensure not to overwrite parameter named with @Param
        // 确保不要覆盖有@Param注解的参数名称
        if (!names.containsValue(genericParamName)) {
          //如果names里面的值有，genericParamName，说明有参数使用了@Param("param1")这样的内容
          //那么该参数值不应该被覆盖
          //否则的话，就需要将泛型名称和对应的值放到param中
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }
}
