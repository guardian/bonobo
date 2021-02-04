curl  -H "Content-Type: application/json" -XPOST --data @add-prod-service.json http://localhost:8001/services
curl  -H "Content-Type: application/json" -XPOST --data @add-prod-key-auth.json http://localhost:8001/services/internal/plugins
curl  -H "Content-Type: application/json" -XPOST --data @add-prod-route.json http://localhost:8001/services/internal/routes

