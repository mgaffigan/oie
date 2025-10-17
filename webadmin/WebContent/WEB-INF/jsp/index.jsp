<!doctype html>
<html lang="en">
<head>
	<meta charset="UTF-8">
	<meta name="viewport" content="width=device-width, initial-scale=1.0">
	
	<title>Engine Login</title>

	<style>
		body {
			font-family: Arial, sans-serif;
			background: linear-gradient(0deg, rgb(255,255,255) 16%, rgb(254,213,216) 100%);
			margin: 0;
			padding: 0;
			display: flex;
			justify-content: center;
			align-items: center;
			height: 100vh;
		}

		#loginCard {
			background: white;
			padding: 2rem;
			border-radius: 8px;
			box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
			width: 300px;
		}

		label {
			display: block;
			margin-bottom: 0.5rem;
			font-weight: bold;
		}

		input[type="text"], input[type="password"] {
			width: 100%;
			padding: 0.5rem;
			margin-bottom: 1rem;
			border: 1px solid #ccc;
			border-radius: 4px;
			box-sizing: border-box;
		}

		input[type="submit"] {
			width: 100%;
			padding: 0.5rem;
			background-color: #007BFF;
			color: white;
			border: none;
			border-radius: 4px;
			cursor: pointer;
			font-size: 1rem;
		}

		#alertBlock {
			background-color: #f8d7da;
			color: #721c24;
			padding: 0.75rem;
			border-radius: 4px;
			margin-bottom: 1rem;
			border: 1px solid #f5c6cb;
		}
	</style>
</head>
    
<body>
<div id="loginCard">
	<img src="images/oie_logo_bottom_text.svg" alt="Open Integration Engine Logo" style="display: block; margin: 0 auto 1rem auto; width: 200px; height: auto;" />

	<div id="alertBlock" style="display: none;">
		Invalid login credentials
	</div>

	<form action="Login.action" method="post">
		<input type="hidden" name="nonce" value="${actionBean.context.nonce}" />

		<label for="username">Username</label>
		<input id="username" type="text" name="username" autofocus required />

		<label for="password">Password</label>
		<input id="password" type="password" name="password" required />

		<input type="submit" value="Sign in"/>
	</form>
</div>

<script>
	document.addEventListener('DOMContentLoaded', function() {
		// Show alert if the showAlert parameter is present
		const showAlert = new URLSearchParams(window.location.search).get('showAlert');
		const alertDiv = document.getElementById('alertBlock');
		if (showAlert) {
			alertDiv.style.display = 'block';
		}
		if (showAlert !== 'true') {
			alertDiv.textContent = showAlert;
		}
	});
</script>

</body>
</html>