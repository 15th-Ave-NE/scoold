"use strict";
$(function () {
	var file;

	$('input[type=file]').on('change', function (e) {
		file = e.target.files[0];
	});

	$('#upload').on('click', function (e) {
		// e.preventDefault();
		var input = $('#file').val();
		if (input == '' || input == undefined) {
			alert("Please select a file to upload.");
		} else {

			var data = new FormData();
			if (file.type.substring(0, 5).toLowerCase() == 'image') {
				data.append('file', file, file.name);

				$.ajax({
					url: '/img/upload',
					type: 'POST',
					data: data,
					cache: false,
					dataType: 'json',
					processData: false,
					contentType: false,
					beforeSend: function (xhr) {
						$('#alert').css('visibility', 'hidden');
						$('#alert').removeClass('alert-success');
						$('#alert').removeClass('alert-danger');
						$('#message').text('');
						$('#aws-link').text('');
						$('#aws-link').css('visibility', 'hidden');
						$('#progress-bar').removeClass('bg-success');
						$('#progress-bar').css('width', 0 + '%');
						$('#progress-bar').text('0%');
						$('#progress-bar').attr('aria-valuenow', 0);
						$('#progress').css('visibility', 'visible');
					},
					success: function (data) {
						$('#file').val('');
						$('#progress').css('visibility', 'hidden');
						$('#alert').css('visibility', 'visible');


						if(data.success.toLowerCase() == "true" ){
							$('#alert').addClass('alert-success');
							$('#alert-heading').text('Upload Completed!');
							$('#aws-link').text(data.url);
							$('#aws-link').attr('href', data.url);
							$('#aws-link').css('visibility', 'visible');
						}else{
							$('#alert').addClass('alert-danger');
							$('#alert-heading').text('Error!');
							$('#aws-link').css('visibility', 'hidden');

						}
						$('#message').text(data.message);
					},
					xhr: function () {
						var xhr = new window.XMLHttpRequest();
						xhr.upload.addEventListener("progress", function (e) {
							if (e.lengthComputable) {
								var percentComplete = e.loaded / e.total;
								percentComplete = parseInt(percentComplete * 100);
								$('#progress-bar').text(percentComplete + '%');
								$('#progress-bar').css('width', percentComplete + '%');

								if (percentComplete == 100) {
									setTimeout(function () {
										$('#progress-bar').addClass('bg-success');
										$('#progress-bar').text('Processing...');
									}, 1000);
								}
							}
						}, false);
						return xhr;
					}
				});
			} else {
				alert("Please select an image file");
			}
		}
	});
});
