curl  -H "Content-Type: application/json" -XPOST --data @add-code-service.json http://localhost:8001/services
curl  -H "Content-Type: application/json" -XPOST --data @add-code-key-auth.json http://localhost:8001/services/internal/plugins
curl  -H "Content-Type: application/json" -XPOST --data @add-code-route.json http://localhost:8001/services/internal/routes

