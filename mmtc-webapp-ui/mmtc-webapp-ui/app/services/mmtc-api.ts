import axios from 'axios';

// const baseUrl = 'http://localhost:8080/backend-api/v1/info/version'
const baseUrl = 'http://localhost:3000/backend-api/v1/info/version'

export interface MmtcVersion {
  version: string
  buildDate: string
  commit: string
}

export async function retrieveMmtcVersion() {
  const response = await axios.get<MmtcVersion>(baseUrl+'/')
  console.log(response.data)
  return response.data
}
