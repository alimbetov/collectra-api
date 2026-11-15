local kumo = require 'kumo'

kumo.on('init', function()
  kumo.define_spool { name = 'data', path = '/var/spool/kumomta/data' }
  kumo.define_spool { name = 'meta', path = '/var/spool/kumomta/meta' }
  kumo.start_http_listener {
    listen = '0.0.0.0:8000',
    trusted_hosts = { '0.0.0.0/0' },
  }
end)

kumo.on('http_message_generated', function(msg)
  local recipient = msg:recipient().email
  if recipient == 'reject@smoke.test' then
    kumo.reject(550, '5.7.1 controlled mail smoke rejection')
  end
  if recipient ~= 'ok@smoke.test' then
    kumo.reject(550, '5.7.1 mail smoke only accepts test recipients')
  end
end)

kumo.on('get_egress_source', function(name)
  return kumo.make_egress_source { name = name }
end)

kumo.on('get_egress_pool', function(name)
  return kumo.make_egress_pool {
    name = name,
    entries = { { name = 'local' } },
  }
end)

kumo.on('get_queue_config', function(domain)
  if domain ~= 'smoke.test' then
    error('mail smoke does not route external domains')
  end
  return kumo.make_queue_config {
    egress_pool = 'local',
    protocol = { smtp = { mx_list = { 'mailpit:1025' } } },
  }
end)

kumo.on('get_egress_path_config', function()
  return kumo.make_egress_path { enable_tls = 'Disabled' }
end)
