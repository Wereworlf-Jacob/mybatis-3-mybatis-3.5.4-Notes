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
package org.apache.ibatis.binding;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.io.ResolverUtil;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 */
public class MapperRegistry {

  private final Configuration config;
  private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<>();

  public MapperRegistry(Configuration config) {
    this.config = config;
  }

  /**
   * 通过Mapper注册中心，获取一个Mapper文件
   * @param type mapper class类
   * @param sqlSession 连接数据源执行语句集的SqlSession
   * @param <T>
   * @return
   */
  @SuppressWarnings("unchecked")
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    //Mapper是一个接口，所以通过代理方式，来创建一个MapperFactory，然后通过工厂来生产Mapper
    //注意，在创建MapperFactory之前，有一个Map容器用来存储该MapperFactory，以保证同一个Mapper仅有一个MapperFactory
    final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
    //从mapper中获取MapperFactory，如果没有获取到，说明未注册该Mapper，则抛出异常
    /**
     * ** 高能注意 **
     * 那么此时mapperFactory是在哪个地方注册上的呢？如果没有注册，会一直报错，肯定就是有问题的
     * 那么回溯该knownMappers，发现在MapperRegistry中有一个addMappers的方法，会往knownMappers里面注册信息
     * 所以一般在同类中有put，get的时候，大部分情况都会有add，set的方法
     * 那么addMapper是什么时候调用的呢，猜测应该是解析xml文件时，识别到Mapper的配置信息，然后注册到该容器中
     * 经过回溯发现
     * 有SqlSessionFactoryBuilder创建Factory的时候，会创建一个XMLConfigBuilder类-> XPathParser类
     * 在SqlSessionFactoryBuilder中会调用XMLConfigBuilder的parse方法，来构建一个Configuration对象
     * 然后在parse方法中会调用XMLConfigBuilder的一个parseConfiguration私有方法
     * 这个里面，读取xml文档对象，解析xml结构数据，当解析到mapper标签时，调用mapperElement方法
     * XMLConfigBuilder中会持有configuration对象，检测到mapper数据后，会调用configuration的addMappers方法
     * 然后Configuration会持有MapperRegistry对象，就可以调用registry的addMappers方法来完成注册
     */
    if (mapperProxyFactory == null) {
      throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
    }
    try {
      //获取到mapperProxyFactory之后，就可以生成一个MapperProxy对象，然后再通过MapperProxy来生成mapper对象
      return mapperProxyFactory.newInstance(sqlSession);
    } catch (Exception e) {
      throw new BindingException("Error getting mapper instance. Cause: " + e, e);
    }
  }

  public <T> boolean hasMapper(Class<T> type) {
    return knownMappers.containsKey(type);
  }

  /**
   * 根据class 也就是Mapper接口的Class信息，注册到MapperRegistry中
   * @param type
   * @param <T>
   */
  public <T> void addMapper(Class<T> type) {
    //首先要判断，只有这个类是接口的情况下，才会注册进去，否则的话是不进行操作的
    if (type.isInterface()) {
      //首先判断，该类是否已经注册过了，如果重复注册会抛出异常，所以如果在package中扫描然后又用Annotation注册，那么结果就是会报错
      if (hasMapper(type)) {
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }
      boolean loadCompleted = false;
      try {
        //注册到knownMapper中，注意注册到MapperRegistry中的不是Mapper interface，而是以Mapper Class为泛型的MapperProxyFactory，该Factory持有Mapper的Class信息
        knownMappers.put(type, new MapperProxyFactory<>(type));
        // It's important that the type is added before the parser is run
        // otherwise the binding may automatically be attempted by the
        // mapper parser. If the type is already known, it won't try.
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        parser.parse();
        loadCompleted = true;
      } finally {
        if (!loadCompleted) {
          knownMappers.remove(type);
        }
      }
    }
  }

  /**
   * @since 3.2.2
   */
  public Collection<Class<?>> getMappers() {
    return Collections.unmodifiableCollection(knownMappers.keySet());
  }

  /**
   * @since 3.2.2
   * 1、来自addMappers(String packageName) 方法的话，superType = Object.class
   */
  public void addMappers(String packageName, Class<?> superType) {
    //通过解决工具ResolverUtil来获取内容 ，具体这个工具类是怎么从包中解析的暂时先不理会
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<>();
    resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
    Set<Class<? extends Class<?>>> mapperSet = resolverUtil.getClasses();
    for (Class<?> mapperClass : mapperSet) {
      //将解析出来的mapper class注册到MapperRegistry中
      addMapper(mapperClass);
    }
  }

  /**
   * @since 3.2.2
   * 从3.2.2版本后才有这个方法
   * 通过包名来注册Mapper信息
   */
  public void addMappers(String packageName) {
    addMappers(packageName, Object.class);
  }

}
