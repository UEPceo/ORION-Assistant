package com.orion.voicelab.brain
import com.orion.voicelab.connectors.ConnectorAction
import com.orion.voicelab.connectors.ConnectorDispatcher
import com.orion.voicelab.connectors.ConnectorRisk
import com.orion.voicelab.connectors.ConnectorResult
fun interface LocalPlanStepHandler { suspend fun run(step: PlannedStep, budget: ExecutionBudget): StepExecution }
/** Bridges approved plan steps to the existing connector security gate. */
class ConnectorPlanStepRunner(
 private val dispatcher: ConnectorDispatcher,
 private val localHandler: LocalPlanStepHandler = LocalPlanStepHandler { step, _ ->
  StepExecution(StepStatus.FAILED, "No hay ejecutor local registrado para ${step.kind}.")
 }
):PlanStepRunner {
 override suspend fun run(step:PlannedStep,budget:ExecutionBudget):StepExecution {
  val connectorId=step.connectorId?:return localHandler.run(step,budget)
  val operation=step.connectorOperation?:return StepExecution(StepStatus.FAILED,"Falta la operación del conector.")
  val risk=when(step.risk){
   PlanRisk.LOW->ConnectorRisk.READ_ONLY
   PlanRisk.MEDIUM->ConnectorRisk.REVERSIBLE_WRITE
   PlanRisk.HIGH->ConnectorRisk.EXTERNAL_SIDE_EFFECT
   PlanRisk.CRITICAL->ConnectorRisk.DESTRUCTIVE
  }
  return when(val result=dispatcher.dispatch(
   action=ConnectorAction(connectorId,operation,step.connectorArguments,risk,step.idempotencyKey),
   userRequestedExactAction=true,
   userConfirmed=step.requiresApproval||step.risk>=PlanRisk.MEDIUM
  )){
   is ConnectorResult.Success->StepExecution(StepStatus.SUCCEEDED,result.summary,result.externalId)
   is ConnectorResult.Failure->StepExecution(StepStatus.FAILED,result.safeMessage)
   is ConnectorResult.NeedsUserInput->StepExecution(StepStatus.BLOCKED,result.prompt)
   is ConnectorResult.ConfirmationRequired->StepExecution(StepStatus.BLOCKED,result.prompt)
  }
 }
}
