-- Development/MVP test accounts. All use password: OrbisFlow123!
INSERT INTO users (id, login_identifier, password_hash, role) VALUES
('10000000-0000-0000-0000-000000000001', 'manager1', '$2b$12$za.pQcHpJWC1pcIpB7gbpuqE8ZEFwTOdzxkaoq0oW6ZoIaE02aYMa', 'manager'),
('10000000-0000-0000-0000-000000000002', 'manager2', '$2b$12$l8VoJfnU4s0WJKjPEqrUBOngWPmXrSHGq50XHbCvj9mj/9dyzH5/G', 'manager'),
('20000000-0000-0000-0000-000000000001', 'finance1', '$2b$12$KR0InRwJLRsr6VfWQwY/7e3Zdsgcai85YxJNAs7GPOP3Zh.LlPzaq', 'finance'),
('20000000-0000-0000-0000-000000000002', 'finance2', '$2b$12$jLBh2/2Zt.nD7gfvUi1AdOripSppRog/7rcfrnQw59DvxPGHKraty', 'finance'),
('30000000-0000-0000-0000-000000000001', 'employee1', '$2b$12$O2jRBFVZGwnqjo7A/8u61uGElX6.w4/u4WVYIjiHFKI7DjPxwszCO', 'employee'),
('30000000-0000-0000-0000-000000000002', 'employee2', '$2b$12$1TGfxwnRfnHx79azOZmvvefRJe6SEMpKls7ULAHW1Xt3WoDhFBWZe', 'employee'),
('30000000-0000-0000-0000-000000000003', 'employee3', '$2b$12$p0i9AEsSEzyprxPzLNMNquJ2tZ4b59HTC5urFECQm4.BB8sT5/3IG', 'employee');

UPDATE users
SET manager_id = CASE
    WHEN login_identifier IN ('employee1', 'employee2')
        THEN '10000000-0000-0000-0000-000000000001'::uuid
    ELSE '10000000-0000-0000-0000-000000000002'::uuid
END
WHERE role = 'employee';
