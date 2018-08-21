{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "description": "A Spark cluster configuration",
  "type": "object",
  "javaInterfaces": ["io.radanalytics.operator.common.EntityInfo"],
  "properties": {
    "name": {
      "type": "string"
    },
    "workerNodes": {
      "type": "integer",
      "default": "1"
    },
    "masterNodes": {
      "type": "integer",
      "default": "1"
    },
    "customImage": {
      "type": "string",
      "default": "jkremser/openshift-spark:2.3-latest"
    },
    "memory": {
      "type": "string"
    },
    "cpu": {
      "type": "string"
    },
    "sparkConfigurationMap": {
      "type": "string"
    },
    "env": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": { "type": "string" },
          "value": { "type": "string" }
        },
        "required": ["name", "value"]
      }
    },
    "sparkConfiguration": { "$ref": "#/properties/env" },
    "downloadData": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "url": { "type": "string" },
          "to": { "type": "string" }
        },
        "required": ["url", "to"]
      }
    }
  },
  "required": [ ]
}