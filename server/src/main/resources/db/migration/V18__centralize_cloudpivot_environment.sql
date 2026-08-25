-- Preserve legacy deployments while making the administrator connection the sole
-- runtime environment source. Personal settings no longer own a service address.
UPDATE system_settings
SET admin_cloudpivot_base_url = cloudpivot_base_url
WHERE (admin_cloudpivot_base_url IS NULL OR TRIM(admin_cloudpivot_base_url) = '')
  AND cloudpivot_base_url IS NOT NULL
  AND TRIM(cloudpivot_base_url) <> '';
