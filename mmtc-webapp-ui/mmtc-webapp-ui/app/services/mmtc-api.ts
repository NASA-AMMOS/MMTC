import axios from 'axios';

// const baseUrl = 'http://localhost:8080/backend-api'
const baseUrl = 'http://localhost:3000/backend-api'

export interface MmtcVersion {
  version: string
  buildDate: string
  commit: string
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
  filenames: string[]
}

export async function retrieveMmtcVersion() {
  const response = await axios.get<MmtcVersion>(baseUrl+'/v1/info/version')
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
