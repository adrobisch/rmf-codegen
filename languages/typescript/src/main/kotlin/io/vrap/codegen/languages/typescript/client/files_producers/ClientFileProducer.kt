package io.vrap.codegen.languages.typescript.client.files_producers

import io.vrap.rmf.codegen.io.TemplateFile
import io.vrap.rmf.codegen.rendring.FileProducer

class ClientFileProducer : FileProducer {


    override fun produceFiles(): List<TemplateFile> {
        return listOf(commonTypes(), localCommonTypes(),produceRequestUtils())
    }

    fun commonTypes() = TemplateFile(relativePath = "base/common-types.ts", content = """
export type MethodType =
  | "GET"
  | "HEAD"
  | "POST"
  | "PUT"
  | "DELETE"
  | "CONNECT"
  | "OPTIONS"
  | "TRACE";

export type VariableMap = {
  [key: string]: string | string[] | number | number[] | boolean | boolean[] | undefined
}

export type MiddlewareArg = {
  request: ClientRequest;
  response?: ClientResponse<any>;
  error?: Error;
  next: Middleware;
};

export type ClientRequest = {
  uri: string,
  method: MethodType,
  body?: any,
  headers?: VariableMap,
}

export type ClientResponse<T> = {
  body: T,
  statusCode?: number,
  headers?: Object
}

export type Middleware = (arg: MiddlewareArg) => Promise<MiddlewareArg>;
""".trim())
}

fun localCommonTypes() = TemplateFile(relativePath = "base/local-common-types.ts", content = """
import { MethodType, VariableMap } from "./common-types";

export interface CommonRequest {
  baseURL: string;
  url?: string,
  headers?: VariableMap;
  method: MethodType;
  uriTemplate: string;
  pathVariables?: VariableMap;
  queryParams?: VariableMap;
  body?: any
}
""".trim())

fun produceRequestUtils() = TemplateFile(relativePath = "base/requests-utils.ts", content = """
import { Middleware, MiddlewareArg, ClientResponse } from "./common-types";
import { CommonRequest } from "./local-common-types";

export class ApiRequestExecutor {
  private middleware: Middleware;
  constructor(
    middlewares: Middleware[]
  ) {
    if (!middlewares || middlewares.length == 0) {
      middlewares = [noOpMiddleware];
    }
    this.middleware = middlewares.reduce(reduceMiddleware);
  }

  public async  execute<O>(request: CommonRequest): Promise<ClientResponse<O>> {
    const { body, headers, method } = request;
    const req = {
      headers,
      method,
      body,
      uri: getURI(request)
    };
    
    const res : MiddlewareArg= await this.middleware({
      request: req,
      next: noOpMiddleware,
    });

    if (res.error) {
      throw res.error;
    }
    
    if(res.response){
      return res.response
    }
    
    return {
      body: {} as O
    }
  }
}

export class ApiRequest<O> {
  constructor(
    private readonly commonRequest : CommonRequest,
    private readonly apiRequestExecutor: ApiRequestExecutor
  ){}

  public execute(): Promise<ClientResponse<O>>{
    return this.apiRequestExecutor.execute(this.commonRequest)
  }
}

function reduceMiddleware(op1: Middleware, op2: Middleware): Middleware {
  return async (arg: MiddlewareArg) => {
    const { next, ...rest } = arg;
    const intermediateOp: Middleware = (tmpArg: MiddlewareArg) => {
      const { next, ...rest } = tmpArg;
      return op2({ ...rest, next: arg.next });
    };

    return op1({
      ...rest,
      next: intermediateOp
    });
  };
}

function getURI(commonRequest: CommonRequest): string {
  const pathMap = commonRequest.pathVariables
  const queryMap = commonRequest.queryParams
  var uri: String = commonRequest.uriTemplate
  var queryParams: string[] = []
  for (const param in pathMap) {
    uri = uri.replace(`{${'$'}{param}}`, `${'$'}{pathMap[param]}`)
  }
  for (const query in queryMap) {
    const queryParameter = queryMap[query]
    if (queryParameter === null || queryParameter === undefined) {
      continue
    }
    if (queryParameter instanceof Array) {
      ;(queryParameter as [])
        .filter(value => value !== null && value !== undefined)
        .forEach(value =>
          queryParams.push(
            `${'$'}{query}=${'$'}{encodeURIComponent(`${'$'}{queryParameter[value]}`)}`
          )
        )
    } else {
      queryParams.push(`${'$'}{query}=${'$'}{encodeURIComponent(`${'$'}{queryParameter}`)}`)
    }
  }
  const resQuery = queryParams.join('&')
  if (resQuery == '') {
    return `${'$'}{commonRequest.baseURL}${'$'}{uri}`
  }
  return `${'$'}{commonRequest.baseURL}${'$'}{uri}?${'$'}{resQuery}`
}

const noOpMiddleware = async (x: MiddlewareArg) => x;
""".trim())
