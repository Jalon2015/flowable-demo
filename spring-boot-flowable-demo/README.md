## 前言

上一篇学习了基于REST API的入门例子；

这一篇我们介绍下最常用的方式，跟Spring Boot的整合

flowable已经有了一个基于Spring Boot的starter，我们可以直接引入

## 目录

1. 创建Spring Boot项目
2. 引入flowable starter依赖
3. 创建流程定义
4. 测试部署状态
5. 修改数据库
6. 对外提供REST接口

## 正文

### 1. 创建Spring Boot项目

这里我们用IDEA直接创建，创建成功后的目录如下所示：

```xml
<dependencies>
    
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
```

### 2. 引入flowable starter依赖

我们先引入`flowable-spring-boot-starter`和`h2`依赖；

还有其他的一些依赖，如下所示：

```xml
<dependencies>
    <dependency>
        <groupId>org.flowable</groupId>
        <artifactId>flowable-spring-boot-starter</artifactId>
        <version>6.7.0</version>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-devtools</artifactId>
        <scope>runtime</scope>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-configuration-processor</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

引入依赖后，我们就可以启动Spring Boot程序，查看打印日志，如下所示：

```bash
2021-11-09 11:55:41.200  INFO 8304 --- [  restartedMain] c.j.s.SpringBootFlowableDemoApplication  : Started SpringBootFlowableDemoApplication in 55.805 seconds (JVM running for 64.253)
```

看到这一行就表示启动成功了；

这里有几点我们需要注意一下：

- Spring Boot启动时，会自动创建h2数据库，用来存放flowable相关数据
- 所有流程引擎相关的Bean都会被创建
- 资源目录下：
  - processes目录内的bpmn流程定义会被自动部署（上一篇我们是调用Flowable REST接口，上传文件进行部署的）
  - case, dmn, forms等目录下的内容也都会被部署

### 3. 创建流程定义

这里我们创建一个流程定义，路径为：`src/main/resources/processes/one-task-process.bpmn20.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions
        xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
        xmlns:flowable="http://flowable.org/bpmn"
        targetNamespace="Examples">

    <process id="oneTaskProcess" name="The One Task Process">
        <startEvent id="theStart" />
        <sequenceFlow id="flow1" sourceRef="theStart" targetRef="theTask" />
        <userTask id="theTask" name="my task" flowable:assignee="jalon" />
        <sequenceFlow id="flow2" sourceRef="theTask" targetRef="theEnd" />
        <endEvent id="theEnd" />
    </process>

</definitions>
```

### 4. 测试部署状态

现在我们编写一个测试方法，用来判断上面的bpmn.xml文件是否部署成功：

```java
package com.jalon.springbootflowabledemo;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringBootFlowableDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootFlowableDemoApplication.class, args);
    }
    
    @Bean
    public CommandLineRunner init(final RepositoryService repositoryService,
                                  final RuntimeService runtimeService,
                                  final TaskService taskService) {

        return strings -> {
            System.out.println("Number of process definitions : "
                    + repositoryService.createProcessDefinitionQuery().count());
            System.out.println("Number of tasks : " + taskService.createTaskQuery().count());
            runtimeService.startProcessInstanceByKey("oneTaskProcess");
            System.out.println("Number of tasks after process start: "
                    + taskService.createTaskQuery().count());
        };
    }
}

```

这里我们注册了一个CommandLineRunner Bean，这个Bean会在Spring Boot启动时执行；

然后我们重新启动应用，打印如下：说明部署成功

```bash
Number of process definitions : 1
Number of tasks : 0
Number of tasks after process start: 1
```

通过打印可以看到，部署了一个流程定义，启动了一个流程任务；

这时如果你重启应用，会发现流程任务还是一个；

是因为默认的数据库h2，重启后数据会丢失；

下面我们可以将其改为MySQL试试

### 5. 修改数据库

首先需要修改依赖，删除h2依赖，引入mysql驱动，并配置数据库参数

```xml
 <dependency>
     <groupId>mysql</groupId>
     <artifactId>mysql-connector-java</artifactId>
     <version>8.0.11</version>
</dependency>
```

修改application.yml，添加数据库配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/flowable-spring-boot?characterEncoding=UTF-8&serverTimezone=UTC&useSSL=false
    username: root
    password: root
```

本地创建一个数据库`flowable-spring-boot`

然后重启应用：启动时，flowable会自动创建数据表并填充相关数据

```bash
2021-11-09 12:32:22.329  INFO 7944 --- [  restartedMain] c.j.s.SpringBootFlowableDemoApplication  : Started SpringBootFlowableDemoApplication in 267.496 seconds (JVM running for 275.771)
Number of process definitions : 1
Number of tasks : 0
Number of tasks after process start: 1
```

此时打印的流程任务为1个，然后我们重启多次，会发现任务数量在增加；

如果是h2数据库，则不会增加，因为h2数据库是基于内存的，重启之后内存数据被清空。

### 6. 对外提供REST接口

通过对外提供接口，可以很方便地在界面进行交互，包括流程的启动、审批、查询等等

下面我们先创建一个Flowable Service，里面包含了两个方法，一个是启动流程，一个是查询任务（通过分配的用户名）

```java
package com.jalon.springbootflowabledemo;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MyService {

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Transactional
    public void startProcess() {
        runtimeService.startProcessInstanceByKey("oneTaskProcess");
    }

    @Transactional
    public List<Task> getTasks(String assignee) {
        return taskService.createTaskQuery().taskAssignee(assignee).list();
    }

}
```

上面的` runtimeService.startProcessInstanceByKey("oneTaskProcess");`中的key就是根据流程定义中的流程id：

```xml
 <process id="oneTaskProcess" name="The One Task Process">
```

下面我们再创建一个控制器，用来处理用户的请求：

```java
package com.jalon.springbootflowabledemo;

import lombok.Data;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
public class MyRestController {

    @Autowired
    private MyService myService;

    @PostMapping(value="/process")
    public void startProcessInstance() {
        myService.startProcess();
    }

    @RequestMapping(value="/tasks", method= RequestMethod.GET, produces= MediaType.APPLICATION_JSON_VALUE)
    public List<TaskRepresentation> getTasks(@RequestParam String assignee) {
        List<Task> tasks = myService.getTasks(assignee);
        List<TaskRepresentation> dtos = new ArrayList<TaskRepresentation>();
        for (Task task : tasks) {
            dtos.add(new TaskRepresentation(task.getId(), task.getName()));
        }
        return dtos;
    }

    @Data
    static class TaskRepresentation {

        private String id;
        private String name;

        public TaskRepresentation(String id, String name) {
            this.id = id;
            this.name = name;
        }
        
    }

}
```

这里我们定义了一个数据传输对象TaskRepresentation，用来封装对外返回的数据，封装的数据为任务id和任务名称；

接下来我们重启应用，通过postman来发送请求；

先启动一个实例：

```bash
curl -X POST  http://localhost:8080/process
```

![image-20211109140747633](https://i.loli.net/2021/11/09/D2bLj4PVeI3l8sG.png)

再查询任务：

```bash
curl http://localhost:8080/tasks?assignee=kermit
```

这里的assignee参数，就是xml中配置的assignee 

```xml
<userTask id="theTask" name="my task" flowable:assignee="jalon" />
```

这里因为我启动了好几次，所以任务有多个

![image-20211109141021501](https://i.loli.net/2021/11/09/FNivYMpxXCtBq5f.png)

## 源码

上传到[github]()

## 总结

本篇主要介绍了将Flowable整合到SpringBoot中，配置MySQL数据库，提供REST支持等内容；

可以看到，整合到Spring Boot中之后，使用起来非常简单方便；

