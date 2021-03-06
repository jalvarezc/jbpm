package org.jbpm.examples.request;

import java.util.HashMap;
import java.util.Map;

import org.drools.KnowledgeBase;
import org.drools.builder.KnowledgeBuilder;
import org.drools.builder.KnowledgeBuilderFactory;
import org.drools.builder.ResourceType;
import org.drools.event.process.DefaultProcessEventListener;
import org.drools.event.process.ProcessStartedEvent;
import org.drools.io.ResourceFactory;
import org.drools.logger.KnowledgeRuntimeLogger;
import org.drools.logger.KnowledgeRuntimeLoggerFactory;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.process.WorkItem;
import org.drools.runtime.process.WorkItemHandler;
import org.drools.runtime.process.WorkItemManager;
import org.drools.runtime.process.WorkflowProcessInstance;
import org.drools.runtime.rule.FactHandle;
import org.jbpm.workflow.instance.node.DynamicNodeInstance;
import org.jbpm.workflow.instance.node.DynamicUtils;

/**
 * This is a sample file to launch a process.
 */
public class ProcessTest {

	public static final void main(String[] args) {
		try {
			// load up the knowledge base
			KnowledgeBase kbase = readKnowledgeBase();
			final StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();
			KnowledgeRuntimeLogger logger = KnowledgeRuntimeLoggerFactory.newThreadedFileLogger(ksession, "test", 1000);
			UIWorkItemHandler handler = new UIWorkItemHandler();
			ksession.getWorkItemManager().registerWorkItemHandler("Human Task", handler);
			handler.setVisible(true);
			ksession.getWorkItemManager().registerWorkItemHandler("Email", new WorkItemHandler() {
				public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
					System.out.println("Sending email ...");
					manager.completeWorkItem(workItem.getId(), null);
				}
				public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
				}
			});
			// start a new process instance
			Person person = new Person("john", "John Doe");
			Request request = new Request("12345");
			request.setPersonId("john");
			request.setAmount(1000L);
			ksession.insert(person);
			FactHandle handle = ksession.insert(request);
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("request", request.getId());
			WorkflowProcessInstance processInstance = (WorkflowProcessInstance) ksession.startProcess("com.sample.requestHandling", params);
			ksession.insert(processInstance);
			// rule validation
			ksession.fireAllRules();
			// signaling to select tasks in the ad-hoc sub-process
			ksession.signalEvent("Investigate", null, processInstance.getId());
			// dynamically adding a new rule that automatically signals the ad-hoc sub-process
			KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
			kbuilder.add(ResourceFactory.newClassPathResource("request/adhoc.drl"), ResourceType.DRL);
			kbase.addKnowledgePackages(kbuilder.getKnowledgePackages());
			ksession.fireAllRules();
			// dynamically adding a new sub-process
			DynamicNodeInstance dynamicNodeInstance = (DynamicNodeInstance)
				processInstance.getNodeInstances().iterator().next();
			DynamicUtils.addDynamicSubProcess(dynamicNodeInstance, ksession, "com.sample.contactCustomer", null);
			logger.close();
			// event processing to detect too much processes being started
			ksession.addEventListener(new DefaultProcessEventListener() {
				public void beforeProcessStarted(ProcessStartedEvent event) {
					ksession.insert(event);
				}
			});
			ksession.startProcess("com.sample.requestHandling");
			ksession.fireAllRules();
			ksession.startProcess("com.sample.requestHandling");
			ksession.fireAllRules();
			ksession.startProcess("com.sample.requestHandling");
			ksession.fireAllRules();
			ksession.startProcess("com.sample.requestHandling");
			ksession.fireAllRules();
			ksession.startProcess("com.sample.requestHandling");
			ksession.fireAllRules();
			// exception handling when canceling request
			request.setCanceled(true);
			ksession.update(handle, request);
			ksession.fireAllRules();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private static KnowledgeBase readKnowledgeBase() throws Exception {
		KnowledgeBuilder kbuilder = KnowledgeBuilderFactory.newKnowledgeBuilder();
		kbuilder.add(ResourceFactory.newClassPathResource("request/requestHandling.bpmn"), ResourceType.BPMN2);
		kbuilder.add(ResourceFactory.newClassPathResource("request/contactCustomer.bpmn"), ResourceType.BPMN2);
		kbuilder.add(ResourceFactory.newClassPathResource("request/validation.drl"), ResourceType.DRL);
		kbuilder.add(ResourceFactory.newClassPathResource("request/eventProcessing.drl"), ResourceType.DRL);
		kbuilder.add(ResourceFactory.newClassPathResource("request/exceptions.drl"), ResourceType.DRL);
		return kbuilder.newKnowledgeBase();
	}
	
}