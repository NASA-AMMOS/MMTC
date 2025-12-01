import type { AvatarProps } from '@nuxt/ui'
import {TimekeepingTelemetryPoint} from "../services/mmtc-api";

export interface ChartData {
  telemetry: TimekeepingTelemetryPoint[]
  correlations: []
}

export interface User {
  id: number
  name: string
  email: string
  avatar?: AvatarProps
  status: UserStatus
  location: string
}
}
