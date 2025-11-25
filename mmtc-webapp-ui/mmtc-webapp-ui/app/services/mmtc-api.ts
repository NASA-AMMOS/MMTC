import axios from 'axios';

// const baseUrl = 'http://localhost:8080/backend-api'
const baseUrl = 'http://localhost:3000/backend-api'

export interface TdtRange{
  minTdt: number,
  maxTdt: number
}

export interface MmtcVersion {
  version: string
  buildDate: string
  commit: string
}

export interface MmtcInformation {
  missionName: string
  mmtcVersion: MmtcVersion
}

export interface ConfigFile {
  filename: string,
  contents: string
}

export interface OutputProductDef {
  name: string,
  builtIn: boolean,
  simpleClassName: string,
  type: string,
  singleFile: boolean,
}

export interface TimekeepingTelemetryPoint {
  originalFrameSample: object,
  tdtG: number,
  scetUtc: string,
  scetErrorMs: number
}

export interface TimeCorrelationTriplet {
  encSclk: string,
  tdtG: string,
  clkchgrate: string,
  scetUtc: string
}

export interface AdditionalSmoothingRecordConfig {
  enabled: boolean,
  coarseSclkTickDuration: number
}

export interface DefaultTimeCorrelationConfig {
  samplesPerSet: number,
  newCorrelationMinTdt: number,
  predictedClkRateMinLookbackHours: number,
  predictedClkRateMaxLookbackHours: number,
  targetSampleRangeStartErt: string,
  targetSampleRangeStopErt: string,
  targetSampleExactErt: string,
  priorCorrelationExactErt: string,
  testModeOwltEnabled: boolean,
  testModeOwltSec: number,
  clockChangeRateAssignedValue: number,
  clockChangeRateModeOverride: string,
  additionalSmoothingRecordConfigOverride: AdditionalSmoothingRecordConfig,
  isDisableContactFilter: boolean,
  isCreateUplinkCmdFile: boolean
}

export interface CorrelationResults {
  correlation : object,
  geometry : object,
  ancillary : object,
  appRunTime : object,
  warnings: string[]
}

export interface TimeCorrelationPreviewResults {
  updatedTriplets: TimeCorrelationTriplet[],
  telemetryPoints: TimekeepingTelemetryPoint[],
  correlationResults: CorrelationResults
}

export async function retrieveMmtcInfo() {
  const response = await axios.get<MmtcInfo>(baseUrl+'/v1/info/info')
  return response.data
}

export async function retrieveConfigurationContent() {
  const response = await axios.get<ConfigFile[]>(baseUrl+'/v1/info/configuration')
  return response.data
}

export async function retrieveOutputProductDefs() {
  const response = await axios.get<OutputProductDef[]>(baseUrl+'/v1/products')
  return response.data
}

export async function retrieveOutputProductFileContents(outputProductDefName: string, filename: string) {
  const response = await axios.get<string>(baseUrl + `/v1/products/${outputProductDefName}/${filename}`)
  return response.data
}

export async function retrieveRunHistoryFileRows() {
  const response = await axios.get<[]>(baseUrl + `/v1/correlation/runhistory`)
  return response.data
}

export async function retrieveTimekeepingTelemetry(beginTime: string, endTime: string, sclkKernelToUseForErrorCalc: string) {
  const response = await axios.get<TimekeepingTelemetryPoint[]>(baseUrl + `/v1/telemetry/range?beginTime=${beginTime}&endTime=${endTime}&sclkKernelName=${sclkKernelToUseForErrorCalc}`)
  return response.data
}

export async function getTimeCorrelations(beginTime: string, endTime: string, sclkKernel: string) {
  const response = await axios.get<TimeCorrelationTriplet>(baseUrl + `/v1/correlation/range?beginTime=${beginTime}&endTime=${endTime}&sclkKernelName=${sclkKernel}`)
  return response.data
}

export async function getAllTimeCorrelations(sclkKernel: string) {
  const response = await axios.get<TimeCorrelationTriplet>(baseUrl + `/v1/correlation/range?sclkKernelName=${sclkKernel}`)
  return response.data
}

export async function getDefaultCorrelationConfig() {
  const response = await axios.get<DefaultTimeCorrelationConfig>(baseUrl + `/v1/correlation/defaultConfig`)
  return response.data
}

export async function rollback(runId: string) {
  const response = await axios.post<TimeCorrelationTriplet>(baseUrl + `/v1/correlation/rollback?runId=${runId}`)
  return response.data
}

export async function runCorrelationPreview(correlationPreviewInput) {
  const response = await axios.post<TimeCorrelationPreviewResults>(baseUrl + `/v1/correlation/preview`, correlationPreviewInput)
  return response.data
}

export async function createCorrelation(correlationInput) {
  const response = await axios.post<object>(baseUrl + `/v1/correlation/create`, correlationInput)
  return response.data
}
