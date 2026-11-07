-- Reference data shared by every environment. Vehicle requirements are the starting assumptions from the
-- business rules; admins change them per category.

INSERT INTO areas (name_en, name_ar) VALUES
 ('Cairo', 'القاهرة'), ('Giza', 'الجيزة'), ('Alexandria', 'الإسكندرية'), ('Qalyubia', 'القليوبية'),
 ('Dakahlia', 'الدقهلية'), ('Sharqia', 'الشرقية'), ('Gharbia', 'الغربية'), ('Monufia', 'المنوفية'),
 ('Beheira', 'البحيرة'), ('Kafr El Sheikh', 'كفر الشيخ'), ('Damietta', 'دمياط'), ('Port Said', 'بورسعيد'),
 ('Ismailia', 'الإسماعيلية'), ('Suez', 'السويس'), ('Faiyum', 'الفيوم'), ('Beni Suef', 'بني سويف'),
 ('Minya', 'المنيا'), ('Asyut', 'أسيوط'), ('Sohag', 'سوهاج'), ('Qena', 'قنا'), ('Luxor', 'الأقصر'),
 ('Aswan', 'أسوان'), ('Red Sea', 'البحر الأحمر'), ('New Valley', 'الوادي الجديد'), ('Matrouh', 'مطروح'),
 ('North Sinai', 'شمال سيناء'), ('South Sinai', 'جنوب سيناء');

INSERT INTO service_categories (code, name_en, name_ar, vehicle_required, active, sort_order, created_at, updated_at) VALUES
 ('SPARE_PARTS', 'Spare parts', 'قطع غيار', TRUE, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 ('BATTERIES', 'Batteries', 'بطاريات', TRUE, TRUE, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 ('TIRES', 'Tires', 'إطارات', TRUE, TRUE, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 ('OIL_CHANGE', 'Oil change', 'تغيير زيت', TRUE, TRUE, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 ('CAR_WASH', 'Car wash', 'غسيل سيارات', FALSE, TRUE, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 ('ACCESSORIES', 'Accessories', 'إكسسوارات', FALSE, TRUE, 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
 ('REPAIRS', 'Repairs and maintenance', 'إصلاح وصيانة', TRUE, TRUE, 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Initial configuration: every fee is zero, as agreed for V1.
INSERT INTO config_versions (config_key, payload, effective_from, created_by, reason, created_at) VALUES
 ('MARKETPLACE', '{"responseDeadlineHours":48,"offerValidityDefaultHours":24,"offerValidityMinHours":1,"offerValidityMaxHours":48,"maxProviderTargets":20,"defaultMatchRadiusKm":30,"noShowGraceMinutes":30,"rankingWindowDays":90,"providerCancellationFlagThreshold":3,"customerCancellationFlagThreshold":5,"disputeFlagThreshold":3}', CURRENT_TIMESTAMP, NULL, 'Initial V1 configuration', CURRENT_TIMESTAMP),
 ('COMMISSION', '{"mode":"NONE","value":0,"payer":"PROVIDER"}', CURRENT_TIMESTAMP, NULL, 'Initial V1 configuration: commission off', CURRENT_TIMESTAMP),
 ('SUBSCRIPTION', '{"monthlyPrice":0,"graceDays":7,"reminderDaysBefore":[7,3]}', CURRENT_TIMESTAMP, NULL, 'Initial V1 configuration: subscription free', CURRENT_TIMESTAMP),
 ('SELLER', '{"maxActiveListings":3,"feeMode":"NONE","feeValue":0,"feeTiming":"ON_PUBLISH","listingDurationDays":30,"moderationRequired":true}', CURRENT_TIMESTAMP, NULL, 'Initial V1 configuration: seller fee off', CURRENT_TIMESTAMP);
