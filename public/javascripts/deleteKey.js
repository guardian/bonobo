Array.from(document.getElementsByClassName('js-delete-key')).forEach(form => {
  form.onsubmit = e => {
    if (!confirm("Are you sure, pal?")) {
      e.stopPropagation();
      e.preventDefault();
    }
  }
});