Array.from(document.querySelectorAll('.js-delete-key')).forEach(btn => {
    btn.onclick = e => {
        e.stopPropagation();
        let req = new XMLHttpRequest();
        let url = btn.getAttribute('data-url');
        let red = btn.getAttribute('data-redirect-url');
        req.onload = req.onerror = () => {
          window.location.href = red;
        };        
        req.open('DELETE', url, true);
        req.send();
    };
});