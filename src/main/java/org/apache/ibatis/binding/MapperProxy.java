/**
 *    Copyright 2009-2020 the original author or authors.
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

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -4724728412955527868L;
  private static final int ALLOWED_MODES = MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED
      | MethodHandles.Lookup.PACKAGE | MethodHandles.Lookup.PUBLIC;
  private static final Constructor<Lookup> lookupConstructor;
  private static final Method privateLookupInMethod;
  private final SqlSession sqlSession;
  private final Class<T> mapperInterface;
  private final Map<Method, MapperMethodInvoker> methodCache;

  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethodInvoker> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  /**
   * 静态代码块，先属性后方法，该静态块在类信息加载时就会执行
   * 调用jdk的MethodHandles类，和其内部类LookUp 1.7和1.8应该都可以通过lookUp(Class<?> class, int allowedModes)来获取到lookUp
   * privateLookupInMethod，属于jdk1.9的方法，所以在1.7和1.8中是没有的
   */
  static {
    Method privateLookupIn;
    try {
      privateLookupIn = MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
    } catch (NoSuchMethodException e) {
      privateLookupIn = null;
    }
    privateLookupInMethod = privateLookupIn;

    Constructor<Lookup> lookup = null;
    if (privateLookupInMethod == null) {
      // JDK 1.8
      try {
        lookup = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
        lookup.setAccessible(true);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(
            "There is neither 'privateLookupIn(Class, Lookup)' nor 'Lookup(Class, int)' method in java.lang.invoke.MethodHandles.",
            e);
      } catch (Exception e) {
        lookup = null;
      }
    }
    lookupConstructor = lookup;
  }

  /**
   * MapperProxy实现InvocationHandler接口后，就需要重写invoke方法，那么调用mapper接口的方法，就会转移到invoke里面来执行
   * 那么mapperProxy是否有这个方法呢？
   * 可以看到，如果执行的是属于Object.class的方法的话，那么就由MapperProxy来执行，因为mapperProxy也是一个Object
   * 否则的话，就调用cachedInvoker来获取一个MapperMethodInvoker，然后调用MapperMethodInvoker的invoke方法
   * 很明显，本来应该由Mapper的实现类target来执行的方法，因为没有target所以要通过其他方法来执行
   * 那么MapperMethodInvoker是怎么获取的，invoke又是干了点什么呢，我们继续往下看。
   * @param proxy jdk生成的代理类
   * @param method 要执行的mapper的方法
   * @param args 参数
   * @return
   * @throws Throwable
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      //如果声明method方法的类是Object，那么就由MapperProxy来执行，因为MapperProxy也是Object
      //比如 toString等类似方法，所以交由MapperProxy来执行也是没有问题的
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args);
      } else {
        //否则的话就由内部类cachedInvoker，来反射执行该方法，proxy: 代理类， method：要执行的方法， args: 参数, sqlSession
        return cachedInvoker(method).invoke(proxy, method, args, sqlSession);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
  }

  /**
   * MapperProxy的私有方法，可缓存的反射类，返回的呢是一个MapperMethodInvoker
   * @param method
   * @return
   * @throws Throwable
   */
  private MapperMethodInvoker cachedInvoker(Method method) throws Throwable {
    try {
      //如果methodCache里面有method方法，那么直接返回，否则的创建一个新的MapperMethodInvoker保存到methodCache中
      return methodCache.computeIfAbsent(method, m -> {
        if (m.isDefault()) { //如果接口方法有 default 关键字修饰，那么就说明这是一个默认方法，通过以下方法创建
          try {
            if (privateLookupInMethod == null) {
              return new DefaultMethodInvoker(getMethodHandleJava8(method));
            } else {
              return new DefaultMethodInvoker(getMethodHandleJava9(method));
            }
          } catch (IllegalAccessException | InstantiationException | InvocationTargetException
              | NoSuchMethodException e) {
            throw new RuntimeException(e);
          }
        } else { //否则通过PlainMethodInvoker来创建
          //构造方法参数是一个MapperMethod，那么就需要创建一个MapperMethod，该类用于执行execute方法，来真正执行mapper interface的方法
          return new PlainMethodInvoker(new MapperMethod(mapperInterface, method, sqlSession.getConfiguration()));
        }
      });
    } catch (RuntimeException re) {
      Throwable cause = re.getCause();
      throw cause == null ? re : cause;
    }
  }

  private MethodHandle getMethodHandleJava9(Method method)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    final Class<?> declaringClass = method.getDeclaringClass();
    return ((Lookup) privateLookupInMethod.invoke(null, declaringClass, MethodHandles.lookup())).findSpecial(
        declaringClass, method.getName(), MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
        declaringClass);
  }

  private MethodHandle getMethodHandleJava8(Method method)
      throws IllegalAccessException, InstantiationException, InvocationTargetException {
    final Class<?> declaringClass = method.getDeclaringClass();
    return lookupConstructor.newInstance(declaringClass, ALLOWED_MODES).unreflectSpecial(method, declaringClass);
  }

  interface MapperMethodInvoker {
    Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable;
  }

  //如果接口方法不是默认方法，那么就会创建一个PlainMethodInvoker来执行invoke方法
  private static class PlainMethodInvoker implements MapperMethodInvoker {
    private final MapperMethod mapperMethod;

    public PlainMethodInvoker(MapperMethod mapperMethod) {
      super();
      this.mapperMethod = mapperMethod;
    }

    /**
     * 也就是兜兜转转，会执行这个invoke方法，那么从这里面可以看出来，方法的执行也不是通过Proxy来执行的，也不是target来执行的。
     * 而是mapperMethod.execute来执行的，那么mapperMethod里面有什么呢？
     * @param proxy
     * @param method
     * @param args
     * @param sqlSession
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
      return mapperMethod.execute(sqlSession, args);
    }
  }

  private static class DefaultMethodInvoker implements MapperMethodInvoker {
    private final MethodHandle methodHandle;

    public DefaultMethodInvoker(MethodHandle methodHandle) {
      super();
      this.methodHandle = methodHandle;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
      return methodHandle.bindTo(proxy).invokeWithArguments(args);
    }
  }
}
