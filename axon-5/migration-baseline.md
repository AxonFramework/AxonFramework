# Migration Baseline

This document is intended to describe the components we:

1. [keep](#1-keep), 
2. provide an [easy migration path](#2-easy-path) for, 
3. provide a [lengthy migration path](#3-lengthy-path) for, and 
4. which we [remove](#4-removal) and thus should preemptively be deprecated in Axon Framework 4.

This is intended to provide a framework upon which we validate acceptable rules for migration towards Axon Framework 5.

## 1. Keep

1. Streaming Event Processors
2. Token Stores
3. Event Stores
4. Message Buses 

## 2. Easy path

1. Annotated-Aggregates -> We should be able to rename/move annotations
2. 

## 3. Lengthy path

1. Merge Direct Query API in Streaming Query API
2. Merge Scatter Gather API in Streaming Query API

## 4. Removal

1. Query ResponseType class -> the query name (and version) is sufficient to dictate the payload and response format.