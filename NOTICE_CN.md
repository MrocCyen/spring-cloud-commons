SpringBoot中各个事件的发布顺序

 - ApplicationStartingEvent
- ApplicationEnvironmentPreparedEvent
- ApplicationContextInitializedEvent
- ApplicationPreparedEvent
- ServletWebServerInitializedEvent（ServletWebServerApplicationContext上下文触发）（spring事件）
- ContextStartedEvent（spring事件）
- ContextRefreshedEvent（spring事件）
- ApplicationStartedEvent
- ApplicationReadyEvent
- ContextClosedEvent（spring事件）

ContextStoppedEvent事件在容器执行了stop方法后进行发布