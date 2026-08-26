package kg.gov.nas.licensedb.dto;

import lombok.Data;

@Data
public class IntegrityLogPattern extends BasePattern {
    /**
     * Источник логов:
     * LOG   -> freq_integrity_log (цепочка: prev_hash/chain_hash)
     * EVENT -> freq_integrity_event (события checkpoint-режима)
     */
    private String source;

    /** Пользователь (actor_username) */
    private String actorUsername;

    /** Действие (INSERT/UPDATE/...) */
    private String action;

    /**
     * ID владельца (owner.ID). Пользователи госорганов чаще оперируют именно этим ID.
     * Если задан, фильтруем логи по всем freq.ID, принадлежащим владельцу.
     */
    private Long ownerId;

    /**
     * Технический идентификатор записи (freq.ID).
     * Поле оставлено для совместимости, но в UI можно не показывать.
     */
    private Long freqId;

    /**
     * Период (локальное время сервера), формат как у input datetime-local:
     * 2026-02-22T10:30
     */
    private String fromDateTime;

    private String toDateTime;
}
