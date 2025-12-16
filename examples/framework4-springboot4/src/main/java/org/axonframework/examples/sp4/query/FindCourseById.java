package org.axonframework.examples.sp4.query;

/**
 * Query message to fetch a single course by its identifier.
 */
public record FindCourseById(String id) {}
