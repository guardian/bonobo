var rateLimitsCheckbox = document.getElementById("defaultRequests_field");

document.addEventListener("DOMContentLoaded", function() {
    rateLimitsCheckbox.style.display = "none";
});

document.getElementById("tier").addEventListener("change", function() {
    rateLimitsCheckbox.style.display = "block";
});