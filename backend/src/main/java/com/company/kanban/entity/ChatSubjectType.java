package com.company.kanban.entity;

/**
 * The record a chat thread is attached to. Chat is offered on interdepartment requests only;
 * TASK and PLANNING remain so chats created by 1.3.0 can still be read until retention removes them.
 */
public enum ChatSubjectType {
    TASK,
    PLANNING,
    REQUEST
}
