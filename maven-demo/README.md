## 前言

学习一个新东西，最好的办法就是实践；

本篇会通过一个简单的maven程序，来对 Flowable 工作流的使用，进行一个简单的入门。

## 目录

1. 创建maven程序
2. 引入依赖
3. 创建主程序
4. 创建一个流程定义
5. 部署流程定义
6. 启动流程
7. 任务查询
8. 排他网关，做决策
9. 决策的实现类
10. 查询历史记录

## 正文

### 1. 创建Maven程序

这里我用的IDEA，创建完如下所示：

![image-20211108122318880](https://i.loli.net/2021/11/08/35aGEWZCgPwnHbm.png)

### 2. 引入依赖

这里我们首先需要引入两个依赖，一个是flowable引擎依赖，一个数据库h2依赖：

```xml
    <dependencies>
        <dependency>
            <groupId>org.flowable</groupId>
            <artifactId>flowable-spring</artifactId>
            <version>6.7.0</version>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <version>1.4.200</version>
        </dependency>
    </dependencies>
```

如果 pom.xml 没有自动更新依赖，那么可以打开maven窗口，点击刷新图标进行手动更新，如下所示：

![](https://i.loli.net/2021/11/08/pBqCvjG2aQtkl89.png)

> 这里没有引入日志依赖，启动时可能会提示，忽略

### 3. 创建主程序

- 主程序中，我们先创建一个**流程引擎的配置实例**，并配置相关参数（主要是数据库相关）
- 然后构建一个**流程引擎**实例

```java
package org.flowable;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;

public class HolidayRequest {
    public static void main(String[] args) {
        // 1. 流程引擎
        // 1.1 流程引擎 配置
        ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:flowable;DB_CLOSE_DELAY=-1")
                .setJdbcUsername("admin")
                .setJdbcPassword("123456")
                .setJdbcDriver("org.h2.Driver")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        // 1.2 构建流程引擎
        ProcessEngine processEngine = cfg.buildProcessEngine();

    }
}
```

### 4. 创建一个流程定义

这里的流程定义就是之前介绍过的 [Flowable UI 中创建流程](https://juejin.cn/post/7025979116679593998))；

下面我们直接用官方给的例子，流程图如下所示

![image-20211108140650841](https://i.loli.net/2021/11/08/nPp1T2UDSsKhjWy.png)

流程定义文件命名为  *holiday-request.bpmn20.xml*:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xmlns:xsd="http://www.w3.org/2001/XMLSchema"
             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
             xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC"
             xmlns:omgdi="http://www.omg.org/spec/DD/20100524/DI"
             xmlns:flowable="http://flowable.org/bpmn"
             typeLanguage="http://www.w3.org/2001/XMLSchema"
             expressionLanguage="http://www.w3.org/1999/XPath"
             targetNamespace="http://www.flowable.org/processdef">

    <process id="holidayRequest" name="Holiday Request" isExecutable="true">

        <startEvent id="startEvent"/>
        <sequenceFlow sourceRef="startEvent" targetRef="approveTask"/>

        <userTask id="approveTask" name="Approve or reject request" flowable:candidateGroups="managers"/>
        <sequenceFlow sourceRef="approveTask" targetRef="decision"/>

        <exclusiveGateway id="decision"/>
        <sequenceFlow sourceRef="decision" targetRef="externalSystemCall">
            <conditionExpression xsi:type="tFormalExpression">
                <![CDATA[
          ${approved}
        ]]>
            </conditionExpression>
        </sequenceFlow>
        <sequenceFlow  sourceRef="decision" targetRef="sendRejectionMail">
            <conditionExpression xsi:type="tFormalExpression">
                <![CDATA[
          ${!approved}
        ]]>
            </conditionExpression>
        </sequenceFlow>

        <serviceTask id="externalSystemCall" name="Enter holidays in external system"
                     flowable:class="org.flowable.CallExternalSystemDelegate"/>
        <sequenceFlow sourceRef="externalSystemCall" targetRef="holidayApprovedTask"/>

        <userTask id="holidayApprovedTask" name="Holiday approved" flowable:assignee="${employee}"/>
        <sequenceFlow sourceRef="holidayApprovedTask" targetRef="approveEnd"/>

        <serviceTask id="sendRejectionMail" name="Send out rejection email"
                     flowable:class="org.flowable.SendRejectionMail"/>
        <sequenceFlow sourceRef="sendRejectionMail" targetRef="rejectEnd"/>

        <endEvent id="approveEnd"/>

        <endEvent id="rejectEnd"/>

    </process>

</definitions>

```

- 前几行 definitions 的属性值可以忽略，这个属于样本代码，在每一个流程定义中都会出现
- 定义中的每个元素都有一个id值（唯一），方便其他元素引用；name值可选，增加可读性
- `<sequenceFlow>` 元素是序列流，就是流程图中的箭头，将前后联系起来

- `<exclusiveGateway>`排他网关，相当于编程中的if/else，后面跟的就是一个条件判断
- `${approved}`是流程变量，配合排他网关一起使用，后面会在流程启动时配置该变量
- `<userTask>`中的`flowable:assignee`是任务要分配给哪个用户，可以是用户组，也可以是具体的用户
  - 第二个用户任务中，分配的用户值 `${employee}`会在流程启动时设定

> PS：如果元素标签显示红色，可忽略，不影响使用

### 5. 部署流程定义

上面我们定义了流程引擎，和流程定义，下面我们就把流程定义文件 bpmn.xml文件部署到流程引擎中

```java

// 2. 流程定义
// 2.1 将 流程定义文件 部署到 流程引擎 中
RepositoryService repositoryService = processEngine.getRepositoryService();
Deployment deployment = repositoryService.createDeployment()
    .addClasspathResource("holiday-request.bpmn20.xml")
    .deploy();
// 2.2 获取部署的 流程定义实例
ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
    .deploymentId(deployment.getId())
    .singleResult();
System.out.println("Found process definition : " + processDefinition.getName());

```

### 6. 启动流程

这里我们用Scanner扫描器来获取用户的输入，然后将其设置为流程变量

然后通过 RuntimeService 启动流程

```java

// 3. 设置流程变量
// 3.1 这里我们用Scan扫描，来从命令行获取
Scanner scanner= new Scanner(System.in);

System.out.println("Who are you?");
String employee = scanner.nextLine();

System.out.println("How many holidays do you want to request?");
Integer nrOfHolidays = Integer.valueOf(scanner.nextLine());

System.out.println("Why do you need them?");
String description = scanner.nextLine();
// 3.2 通过 RuntimeService 启动流程
RuntimeService runtimeService = processEngine.getRuntimeService();
Map<String, Object> variables = new HashMap<>();
variables.put("employee", employee);
variables.put("nrOfHolidays", nrOfHolidays);
variables.put("description", description);
ProcessInstance processInstance =
    runtimeService.startProcessInstanceByKey("holidayRequest", variables);

```

运行后的命令行如下所示：

![image-20211108150144809](https://i.loli.net/2021/11/08/x9mgs2QoP7BY1hE.png)

### 7. 任务查询

这里我们是通过 **经理** 的角色来查询任务，如下所示：

```java
// 4. 任务查询
TaskService taskService = processEngine.getTaskService();
List<Task> tasks = taskService.createTaskQuery().taskCandidateGroup("managers").list();
System.out.println("You have " + tasks.size() + " tasks:");
for (int i=0; i<tasks.size(); i++) {
    System.out.println((i+1) + ") " + tasks.get(i).getName());
}
```

打印如下：

```bash
You have 1 tasks:
1) Approve or reject request
```

### 8. 排他网关，做决策

这里我们通过是以及经理的身份，在命令行选中对应的任务，然后来作决策；

此处就是给流程变量 `${approved}`赋值

```java
// 5. 排他网关的逻辑：这里我们通过命令行选中对应的任务，来作决策
System.out.println("Which task would you like to complete?");
int taskIndex = Integer.valueOf(scanner.nextLine());
Task task = tasks.get(taskIndex - 1);
Map<String, Object> processVariables = taskService.getVariables(task.getId());
System.out.println(processVariables.get("employee") + " wants " +
        processVariables.get("nrOfHolidays") + " of holidays. Do you approve this?");

boolean approved = scanner.nextLine().toLowerCase().equals("y");
variables = new HashMap<String, Object>();
variables.put("approved", approved);
taskService.complete(task.getId(), variables);
```

打印如下：这里我们输入`y`表示同意请假

```bash
1 wants 1 of holidays. Do you approve this?
y
```

### 9. 决策的实现类

之前我们在bpmn.xml的流程定义配置文件中，定义了一个决策类，就是请假请求被同意之后，调用的类，如下所示

```xml
<serviceTask id="externalSystemCall" name="Enter holidays in external system"
             flowable:class="org.flowable.CallExternalSystemDelegate"/>
```

现在我们实现这个类

```java
package org.flowable;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;

public class CallExternalSystemDelegate implements JavaDelegate {

    public void execute(DelegateExecution execution) {
        System.out.println("Calling the external system for employee "
                + execution.getVariable("employee"));
    }

}
```

同意之后，会打印如下：后面的1就是 变量 ${employee}

```bash
Calling the external system for employee 1
```

### 10. 查询历史记录

最后，我们来查询一下刚才执行的流程的整个经过

```java
// 6. 查询历史记录
HistoryService historyService = processEngine.getHistoryService();
List<HistoricActivityInstance> activities =
        historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstance.getId())
                .finished()
                .orderByHistoricActivityInstanceEndTime().asc()
                .list();

for (HistoricActivityInstance activity : activities) {
    System.out.println(activity.getActivityId() + " took "
            + activity.getDurationInMillis() + " milliseconds");
}
```

整个流程的经过如下：

```bash
startEvent took 5 milliseconds
_flow_startEvent__approveTask took 0 milliseconds
approveTask took 340499 milliseconds
_flow_approveTask__decision took 0 milliseconds
_flow_decision__externalSystemCall took 0 milliseconds
decision took 5 milliseconds
_flow_externalSystemCall__holidayApprovedTask took 0 milliseconds
externalSystemCall took 3 milliseconds
```

## 完整例子

以上传到github

## 总结

上面主要通过Java程序的命令行来完成了一个流程，目的是为了入门，对流程的基本概念有个了解；

平时实际使用多是用UI界面来完成。

