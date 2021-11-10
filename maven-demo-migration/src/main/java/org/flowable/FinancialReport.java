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
