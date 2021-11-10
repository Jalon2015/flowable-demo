## 前言

前面我们接触了很多Flowable以及Flowable UI相关方面的例子；

这篇我们再通过一个例子，来系统地了解一下BPMN以及Flowable的相关概念；

为了熟悉Flowable的相关API，这里我们用的例子是基于JavaAPI的。

同时结合了[flowable-ui相关知识](https://juejin.cn/post/7025979116679593998)

## 目录

1. 背景介绍
2. 流程定义
3. 部署流程
4. 配置用户、组、权限
5. UI界面启动流程
6. 代码层面启动流程

## 正文

### 1. 背景介绍

这里的案例是基于一个公司来讲的，比如一个公司有股东，有经理，有财务；

每个月财务都需要把报表提交给上级经理，经理审批通过后，会发送给股东。

### 2. 流程定义

这里我们先看一个图，如下所示：这里我们直接引用官方的图

![image-20211109143551966](https://i.loli.net/2021/11/09/9dHDnRvhZjx7osJ.png)

其中包括的元素有：

- 空的启动事件
- 用户任务-写每个月的财务报表
- 用户任务-审核每个月的财务报表
- 空的结束事件

下面我们就来编写这个bpmn.xml文件：`FinancialReportProcess.bpmn20.xml`

```xml
<definitions id="definitions"
  targetNamespace="http://flowable.org/bpmn20"
  xmlns:flowable="http://flowable.org/bpmn"
  xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">

    <process id="financialReport" name="Monthly financial report reminder process">

      <startEvent id="theStart" />

      <sequenceFlow id="flow1" sourceRef="theStart" targetRef="writeReportTask" />

      <userTask id="writeReportTask" name="Write monthly financial report" >
        <documentation>
          Write monthly financial report for publication to shareholders.
        </documentation>
        <potentialOwner>
          <resourceAssignmentExpression>
            <formalExpression>accountancy</formalExpression>
          </resourceAssignmentExpression>
        </potentialOwner>
      </userTask>

      <sequenceFlow id="flow2" sourceRef="writeReportTask" targetRef="verifyReportTask" />

      <userTask id="verifyReportTask" name="Verify monthly financial report" >
        <documentation>
          Verify monthly financial report composed by the accountancy department.
          This financial report is going to be sent to all the company shareholders.
        </documentation>
        <potentialOwner>
          <resourceAssignmentExpression>
            <formalExpression>management</formalExpression>
          </resourceAssignmentExpression>
        </potentialOwner>
      </userTask>

      <sequenceFlow id="flow3" sourceRef="verifyReportTask" targetRef="theEnd" />

      <endEvent id="theEnd" />

    </process>

</definitions>
```

### 3. 部署流程

这里我们创建一个maven程序，将刚才创建的流程定义部署到资源目录下，完整路径为：`src/main/resources/FinancialReportProcess.bpmn20.xml`

主程序如下所示：这里我们直接运行主程序，就可以把流程定义部署到flowable中，这里的实际存储地址就是程序中的那个h2数据库

```java
package org.flowable;

import org.flowable.engine.*;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FinancialReport {
    public static void main(String[] args) {
        // 1. 流程引擎
        // 1.1 流程引擎 配置
        ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:~/flowable-db/engine-db;AUTO_SERVER=TRUE;AUTO_SERVER_PORT=9093;DB_CLOSE_DELAY=-1")
                .setJdbcUsername("flowable")
                .setJdbcPassword("flowable")
                .setJdbcDriver("org.h2.Driver")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        // 1.2 构建流程引擎
        ProcessEngine processEngine = cfg.buildProcessEngine();

        // 2. 流程定义
        // 2.1 将 流程定义文件 部署到 流程引擎 中
        RepositoryService repositoryService = processEngine.getRepositoryService();
        Deployment deployment = repositoryService.createDeployment()
                .addClasspathResource("FinancialReportProcess.bpmn20.xml")
                .deploy();
        
    }
}

```

> PS：程序中的h2数据库配置 跟 官方的 flowable-ui.war 例子中的 h2 数据库是保持一致的（通过解压flowable-ui.war即可看到相关参数配置），方便后续的界面化操作

这里我们需要明白一点：流程启动后，每执行一步，都会将这一步的状态和数据保存到数据库中，此时就算流程重启，数据也不会丢失；

比如流程启动后，执行了空的启动事件，此时来到第一个用户任务：任务内容是填写财务报表，任务的分配人/组是财务组，任务的状态为等待，那么这几个数据就会保存到数据库中；

当这个任务完成时，流程才会继续往下走，走到下一个任务，继续等待（同时存储任务的相关数据）。

> 这里的部署其实有很多种方式，详细参考：https://wwv.flowable.com/open-source/docs/bpmn/ch06-Deployment

### 4. 配置用户、组、权限

下面我们需要涉及到之前[flowable-ui介绍的东西](https://juejin.cn/post/7025979116679593998)；

先运行flowable-ui.war程序：[下载地址](https://www.flowable.com/open-source/downloads/)

```bash
java -jar flowable-ui.war
```

启动成功之后，访问: http://localhost:8080/flowable-ui/

用admin/test登录之后，分别执行如下操作：

- 进入IDM管理界面，创建两个用户：jalon 和 tangyuan（这个名字随便起）
- 给上面这两个用户分配工作流权限
- 创建两个组：accountancy 和 management（一个是财务组，一个是经理组，在xml中有定义）
- 将jalon添加到accountancy 组，将 tangyuan 添加到 management 这个组 

### 5. UI界面启动流程

- 然后用jalon账号登录，进入**任务应用程序**之后，点击**启动流程**，就能看到刚才部署的流程定义

![image-20211109153652930](https://i.loli.net/2021/11/09/IpkYUCOKJ9yLcax.png)

- 启动之后，点击**活动任务**进去，然后**认领**任务，因为这里没有表单需要填写，所以直接点击**完成**即可

![image-20211109153847038](https://i.loli.net/2021/11/09/cGTPJ53UNyYsMzA.png)

![image-20211109153924333](https://i.loli.net/2021/11/09/7qQVPuN8gxdfolA.png)

![image-20211109154012249](https://i.loli.net/2021/11/09/RJhmOvuc2VwWUMl.png)



- 然后用tangyuan账号登录，就可以看到jalon启动的流程，操作步骤类似，先认领，再完成

### 6. 代码层面启动流程

上面我们用UI实现了一个流程的完整过程，下面我们用代码来实现下：

```java
package org.flowable;

import org.flowable.engine.*;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FinancialReport {
    public static void main(String[] args) {
        // 1. 流程引擎
        // 1.1 流程引擎 配置
        ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:~/flowable-db/engine-db;AUTO_SERVER=TRUE;AUTO_SERVER_PORT=9093;DB_CLOSE_DELAY=-1")
                .setJdbcUsername("flowable")
                .setJdbcPassword("flowable")
                .setJdbcDriver("org.h2.Driver")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        // 1.2 构建流程引擎
        ProcessEngine processEngine = cfg.buildProcessEngine();

        // 2. 流程定义
        // 2.1 将 流程定义文件 部署到 流程引擎 中
//        RepositoryService repositoryService = processEngine.getRepositoryService();
//        Deployment deployment = repositoryService.createDeployment()
//                .addClasspathResource("FinancialReportProcess.bpmn20.xml")
//                .deploy();
        // 3. 启动流程
        RuntimeService runtimeService = processEngine.getRuntimeService();
        String procId = runtimeService.startProcessInstanceByKey("financialReport").getId();

        // 4. 领取任务
        TaskService taskService = processEngine.getTaskService();
        List<Task> tasks = taskService.createTaskQuery().taskCandidateGroup("accountancy").list();
        for (Task task : tasks) {
            System.out.println("Following task is available for accountancy group: " + task.getName());

            // claim it
            taskService.claim(task.getId(), "jalon");
        }

        // 查看领取的任务
        tasks = taskService.createTaskQuery().taskAssignee("jalon").list();
        for (Task task : tasks) {
            System.out.println("Task for jalon: " + task.getName());

            // Complete the task
            taskService.complete(task.getId());
        }

        System.out.println("Number of tasks for jalon: "
                + taskService.createTaskQuery().taskAssignee("jalon").count());

        // 5. 经理领取任务
        tasks = taskService.createTaskQuery().taskCandidateGroup("management").list();
        for (Task task : tasks) {
            System.out.println("Following task is available for management group: " + task.getName());
            taskService.claim(task.getId(), "tangyuan");
        }

        // 6. 完成任务
        for (Task task : tasks) {
            taskService.complete(task.getId());
        }

        // 7. 查看任务是否完成
        HistoryService historyService = processEngine.getHistoryService();
        HistoricProcessInstance historicProcessInstance =
                historyService.createHistoricProcessInstanceQuery().processInstanceId(procId).singleResult();
        System.out.println("Process instance end time: " + historicProcessInstance.getEndTime());
    }
}

```

## 源码

上传在[github](https://github.com/Jalon2015/flowable-demo/tree/master/maven-demo-v2)

## 总结

这一篇主要是将代码和UI结合起来做了演示，虽然例子简单，但是概念都大同小异；







