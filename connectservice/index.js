import express from 'express';
import * as yup from 'yup';
import { postRegistration, postUsage, registrationBodySchema, usageBodySchema } from './usage.js';
import { postNotifications, notificationBodySchema } from './notifications.js';

const app = express();

app.use(express.urlencoded({ extended: true }));

app.post('/RegistrationServlet', [validateFormUrlEncoded, makeValidator(registrationBodySchema)], postRegistration);
app.post('/UsageStatisticsServlet', [validateFormUrlEncoded, makeValidator(usageBodySchema)], postUsage);

app.post('/NotificationServlet', [validateFormUrlEncoded, makeValidator(notificationBodySchema)], postNotifications);

const port = process.env.PORT || 3000;
app.listen(port, () =>
    console.log(`connectservice available from http://localhost:${port}`),
);

function validateFormUrlEncoded(req, res, next) {
    const contentType = req.headers['content-type'];
    if (!contentType || !contentType.startsWith('application/x-www-form-urlencoded')) {
        return res.status(415).send('Unsupported content-type');
    }

    next();
}

function makeValidator(schema) {
    return async (req, res, next) => {
        try {
            // abortEarly: false collects all errors rather than stopping on the first
            await schema.validate(req.body, { abortEarly: false });
            next();
        } catch (error) {
            if (error instanceof yup.ValidationError) {
                return res.status(400).json({
                    type: error.name,
                    message: error.message,
                    errors: error.inner.map(err => ({
                        path: err.path,
                        message: err.message,
                        type: err.type,
                    })),
                });
            }
            next(error);
        }
    };
}
