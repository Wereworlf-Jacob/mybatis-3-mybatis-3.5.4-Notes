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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.ibatis.binding.MapperProxy.MapperMethodInvoker;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Lasse Voss
 */
public class MapperProxyFactory<T> {

  private final Class<T> mapperInterface;
  private final Map<Method, MapperMethodInvoker> methodCache = new ConcurrentHashMap<>();

  public MapperProxyFactory(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  public Class<T> getMapperInterface() {
    return mapperInterface;
  }

  public Map<Method, MapperMethodInvoker> getMethodCache() {
    return methodCache;
  }

  /**
   * 代理模式通过MapperProxy，来生成一个Mapper实例
   * 此处需要注意一点就是，我们使用代理模式，正常情况下是，我代理类需要持有被代理对象的实例
   * 例如下面内部类mepo 作为代理类持有 实现person接口的一个实例，也就是zhangsan
   * 那么当我使用时，创建一个mepo实例，然后持有一个zhangsan实例，然后创建一个person代理实例，来调用方法
   * 然而此处使用的jdk动态代理，并没有持有一个Mapper实例，仅仅是拿到了Mapper的interface信息，那么他又是如何通过反射来使Mapper代理类来调用方法呢？
   * mapperProxy是mapper的代理类，它实现了InvoiceHandler接口，所以去看看他的invoke方法干了点什么
   *
   * @param mapperProxy
   * @return
   */
  @SuppressWarnings("unchecked")
  protected T newInstance(MapperProxy<T> mapperProxy) {
    return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[] { mapperInterface }, mapperProxy);
  }

  /**
   * 工厂类来生成产品，就是MapperProxy对象
   * @param sqlSession sqlSession对象
   * @return
   */
  public T newInstance(SqlSession sqlSession) {
    //生成一个MapperProxy代理对象，mapperInterface就是xml配置的Mapper Interface类，methodCache是一个concurrentMap
    final MapperProxy<T> mapperProxy = new MapperProxy<>(sqlSession, mapperInterface, methodCache);
    return newInstance(mapperProxy);
  }

  class proxyTest{
    public void test(){
      mepo mepo = new mepo();
      person person = (MapperProxyFactory.person) mepo.newInstance(new zhangsan());
      person.getGrilFriend();
    }
  }

  class mepo implements InvocationHandler {

    private person target;

    public Object newInstance(person ren){
      this.target = ren;
      return Proxy.newProxyInstance(target.getClass().getClassLoader(), target.getClass().getInterfaces() , this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      return method.invoke(this.target, args);
    }
  }

  class zhangsan implements person {
    @Override
    public void getGrilFriend() {
      System.out.println("fdsfdsfds");
    }
  }

  interface person {
    void getGrilFriend();
  }

}
