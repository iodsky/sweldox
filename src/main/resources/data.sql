INSERT INTO department (id, title)
VALUES ('IT', 'INFORMATION TECHNOLOGY'),
       ('HR', 'HUMAN RESOURCES'),
       ('CORP', 'CORPORATE'),
       ('ACC', 'ACCOUNTING'),
       ('PAY', 'PAYROLL'),
       ('SAL', 'SALES'),
       ('LOG', 'LOGISTICS'),
       ('CS', 'CUSTOMER SERVICE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO position (id, title, department_id)
VALUES ('CEO', 'Chief Executive Officer', 'CORP'),
       ('COO', 'Chief Operating Officer', 'CORP'),
       ('CFO', 'Chief Finance Officer', 'CORP'),
       ('CMO', 'Chief Marketing Officer', 'CORP'),
       ('CSR', 'Customer Service and Relations', 'CS'),
       ('ITOPSYS', 'IT Operations and Systems', 'IT'),
       ('HRM', 'HR Manager', 'HR'),
       ('HRTL', 'HR Team Leader', 'HR'),
       ('HRRL', 'HR Rank and File', 'HR'),
       ('ACCHEAD', 'Accounting Head', 'ACC'),
       ('ACCMNGR', 'Account Manager', 'ACC'),
       ('ACCTL', 'Account Team Leader', 'ACC'),
       ('ACCRL', 'Account Rank and File', 'ACC'),
       ('PAYRMNGR', 'Payroll Manager', 'PAY'),
       ('PAYRL', 'Payroll Rank and File', 'PAY'),
       ('PAYTL', 'Payroll Team Leader', 'PAY'),
       ('SCL', 'Supply Chain and Logistics', 'LOG'),
       ('SLMKT', 'Sales and Marketing', 'SAL')
ON CONFLICT (id) DO NOTHING;

INSERT INTO deduction_type (code, type)
VALUES ('SSS', 'Social Security System'),
       ('PHIC', 'PhilHealth'),
       ('HDMF', 'Pag-IBIG'),
       ('TAX', 'Withholding Tax')
ON CONFLICT (code) DO NOTHING;

INSERT INTO benefit_type (id, type)
VALUES ('MEAL', 'MEAL ALLOWANCE'),
       ('PHONE', 'PHONE ALLOWANCE'),
       ('CLOTHING', 'CLOTHING ALLOWANCE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO employee (id, first_name, last_name, birthday, address, phone_number)
VALUES (10000, 'Super', 'User', '1990-01-15', '123 Test Street, Manila, Philippines', '+639171234567')
ON CONFLICT (id) DO NOTHING;

INSERT INTO government_id (id, employee_id, sss_no, tin_no, philhealth_no, pagibig_no)
VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 10000, '34-1234567-8', '123-456-789-000', '12-345678901-2', '1234-5678-9012')
ON CONFLICT (id) DO NOTHING;

INSERT INTO employment_details (id, employee_id, supervisor_id, position_id, department_id, status)
VALUES ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 10000, NULL, 'ITOPSYS', 'IT', 'REGULAR')
ON CONFLICT (id) DO NOTHING;

INSERT INTO compensation (id, employee_id, basic_salary, hourly_rate, semi_monthly_rate)
VALUES ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 10000, 50000.00, 297.62, 25000.00)
ON CONFLICT (id) DO NOTHING;

INSERT INTO user_role (role)
VALUES ('IT'), ('HR'), ('PAYROLL'), ('EMPLOYEE')
ON CONFLICT (role) DO NOTHING;

INSERT INTO users (id, employee_id, email, password, role_id, created_at, updated_at)
VALUES ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 10000, 'superuser@motorph.com', '$2a$12$lMIUx49rQdGhsrfLbQB3Hetueio4UgmdWV/Vcw3KweucDgZ6fDs/a', 'IT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
