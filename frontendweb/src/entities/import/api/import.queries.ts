import {queryOptions} from '@tanstack/react-query';
import {getImport,getImportErrors,getImports} from './import.api';
import type {ImportListParams} from '../model/import.types';
export const importKeys={all:['imports'] as const,list:(p:ImportListParams)=>['imports','list',p] as const,detail:(id:string)=>['imports','detail',id] as const,errors:(id:string,page:number)=>['imports','errors',id,page] as const};
export const importQueries={list:(p:ImportListParams)=>queryOptions({queryKey:importKeys.list(p),queryFn:()=>getImports(p)}),detail:(id:string)=>queryOptions({queryKey:importKeys.detail(id),queryFn:()=>getImport(id),enabled:Boolean(id)}),errors:(id:string,page:number)=>queryOptions({queryKey:importKeys.errors(id,page),queryFn:()=>getImportErrors(id,page),enabled:Boolean(id)})};
