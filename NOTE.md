### BaseExecutor#queryFromDatabase 322行代码意义
### Mybais plugin实现机制
**通过代理模式实现的，使用责任链模式**
 1. 在xml加载时，会加载所有的plugs到interceptorChain的interceptors容器里面
 1. 在创建比如Executor时，会将该Executor通过拦截器责任链(interceptorChain)，将Executor包裹一层调用wrap方法（即代理一下）
 1. 如果在该拦截器中，配置的注解中，拦截了该class类，比如PageHelper就拦截了Executor.class类，
      那么就需要将该Executor通过动态代理，来创建一个持有Executor的Plugin代理对象，然后由代理对象，来代替Executor执行所有方法
 1. 执行方法的时候，就需要判断，该方法是否在拦截器中进行了拦截
 1. 如果进行了拦截，那么就需要将Executor传到对应的Inteceptor类中，然后调用intercept方法
    比如：pageHelper的intercept方法。同时封装一个Invocation对象（执行目标：Executor对象，执行方法，参数）
	然后在intercept方法中，进行各种代理处理之后，再通过反射（invoke方法），让目标类执行自己的方法内容
 1. 如果该方法没有被拦截器拦截，那么就不进行任何代理操作，直接通过反射，让目标类执行自己的方法，同时返回执行结果。
### lazy loading 是怎么做到的？
### 如果在xml配置同一个mapper类，扫描到两次会怎么样？
  会提示该mapper已经在注册容器中存在了Exception信息
  因为在扫描mapper类的时候，使用了MapperRegistry，在该注册器中有一个knownMappers继承了HashMap，是一个容器
  扫描到的mapper都会创建一个持有该类型的MapperProxyFactory存放到该容器中，在存放之前会有已存在校验
### 如果同一个方法名，同时使用注解和xml配置会发生什么？
  会提示该方法已经在集合中匹配到了Exception信息
  因为MappedStatement对象，使用了自定义的StrictMap（严格的map集合）保存
  该map继承自HashMap，同时重写了put方法，在put之前如果检查到key已经存在，那么就会抛出异常