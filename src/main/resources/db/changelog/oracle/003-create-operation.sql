--liquibase formatted sql

--changeset beadle-protech:003-create-operation dbms:oracle,h2
UPDATE BEADLE_PROTECH_CALL_LOG SET OPERATION = 'create' WHERE OPERATION = 'raise';
--rollback UPDATE BEADLE_PROTECH_CALL_LOG SET OPERATION = 'raise' WHERE OPERATION = 'create';
