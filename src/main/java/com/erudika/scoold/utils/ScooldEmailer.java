/*
 * Copyright 2013-2021 Erudika. https://erudika.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * For issues and patches go to: https://github.com/erudika
 */
package com.erudika.scoold.utils;

import com.erudika.para.email.Emailer;
import com.erudika.para.utils.Config;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import javax.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

public class ScooldEmailer implements Emailer {

	private static final Logger logger = LoggerFactory.getLogger(ScooldEmailer.class);
	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(Config.EXECUTOR_THREADS);
	private JavaMailSender mailSender;

	public ScooldEmailer(JavaMailSender mailSender) {
		this.mailSender = mailSender;
	}

	@Override
	public boolean sendEmail(final List<String> emails, final String subject, final String body) {
		if (emails == null || emails.isEmpty()) {
			return false;
		}
		asyncExecute(() -> {
			emails.forEach(email -> {
				try {
					mailSender.send((MimeMessage mimeMessage) -> {
						MimeMessageHelper msg = new MimeMessageHelper(mimeMessage);
						msg.setTo(email);
						msg.setSubject(subject);
						msg.setFrom(Config.SUPPORT_EMAIL, Config.APP_NAME);
						msg.setText(body, true); // body is assumed to be HTML
					});
					logger.debug("Email sent to {}, {}", email, subject);
				} catch (MailException ex) {
					logger.error("Failed to send email to {} with body [{}]. {}", email, body, ex.getMessage());
				}
			});
		});
		return true;
	}

	private void asyncExecute(Runnable runnable) {
		if (runnable != null) {
			try {
				EXECUTOR.execute(runnable);
			} catch (RejectedExecutionException ex) {
				logger.warn(ex.getMessage());
				try {
					runnable.run();
				} catch (Exception e) {
					logger.error(null, e);
				}
			}
		}
	}

}
