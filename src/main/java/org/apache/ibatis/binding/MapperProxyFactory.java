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

  /**
   * 这个地方，保存了一个MethodCache是做什么的呢？
   * 而在根据COC原则：惯例大于配置
   * 既然有get方法，在当前类就应该有一个set方法，明显这里面没有set的地方
   * 那么在哪里set的呢？
   * 经过代码追踪发现，在该类newInstance的时候，将methodCache传给了MethodProxy
   * 然后在methodProxy.cachedInvoker的时候，创建了MapperMethodInvoker，将invoker保存到了methodCache中
   * mybatis使用这种方法应该是用来解决某种问题，但是对于开发来讲，尽量不要使用这种方法。
   * 对于methodCache用法的猜测如下
   * 1、Mapper的class信息以及Mapper.xml sqlStatement信息是在xml加载的过程中进行解析的
   * 2、Mapper相关信息加载后，是保存在一个MapperProxyFactory容器里面的。
   * 3、而从容器中，获取一个Mapper对象，是在getMapper获取具体Mapper对象的时候进行解析的
   * 4、getMapper的时候，就从MapperProxyFactory里面获得了一个MapperProxy对象，通过代理对象来执行CURD操作
   * 5、而执行不同的方法，比如接口存在默认方法的情况，执行Object的方法，执行普通方法，进行的处理都不一样
   * 6、所以Mybatis创建了不同的MapperMethodInvoker(映射方法执行器)来执行各种不同的方法
   * 7、而为了提高系统性能，mybatis需要将MapperMethodInvoker缓存起来。
   * 8、按照面向对象概念，Mapper的MethodInvoker信息应该属于Mapper class 所属。
   * 9、所以MapperMethodInvoker应该是MapperProxyFactory持有
   *    （因为Mapper是接口，一个mapper对应一个factory, 每个factory生产不同的mapper，所以应该由Factory持有，然后从mapperFactory中创建MapperProxy的时候，再由MapperProxy持有）
   * 10、正常应该在MapperProxy执行的时候，就应该从Factory中拿到了MapperMethodInvoker，
   *     但是因为MapperProxy是每次getMapper时都会创建的，是不一样的。所以MapperProxy无法去缓存MapperMethodInvoker
   *     而MapperProxyFactory在缓存Mapper信息时，是不会解析interface的Method信息的，因为这个时候还没有MethodProxy对象，
   *     即使创建了MapperMethodInvoker那么也不能正常运行，这个明显是不符合常理的。
   *     而解析Method方法又很简单，不像xml解析那么复杂，所以就退而求其次，用Factory来缓存invoker，然后每次调用mapper的方法时，
   *     在MapperProxy.invoke的时候，再向MethodCache容器进行MapperMethodInvoker的缓存。
   * @return
   */
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
